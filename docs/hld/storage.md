# HLD — 04: Storage

## Storage Decision Per Service

| Service | Hot Path Store | Transactional Store | Why |
|---|---|---|---|
| Location Ingestion | Redis (geo-set) | — | Sub-ms geo-writes; no durability needed (re-hydrated from heartbeats) |
| Dispatch | Redis (read: geo, status, offer TTL) | CockroachDB (write: ride_requests, dispatch_offers) | Read from Redis for speed; persist decisions for audit |
| Surge Pricing | Redis (read+write: supply, demand, multiplier) | Postgres (snapshot for analytics) | Pure hot-path; eventual consistency acceptable |
| Trip Lifecycle | Redis (read: surge multiplier at match confirmation) | CockroachDB (trips, trip_pauses) | Trip records are financial — need ACID |
| Payment | — | CockroachDB (payments) | PCI compliance; ACID; idempotency via unique constraint |
| Notification | — | — | Fire-and-forget; no state needed |
| Admin/Ops | — | Postgres (feature flags, config) | Low traffic; simple relational rows |

---

## Redis Key Catalogue

| Key | Type | Contents | Used by | TTL |
|---|---|---|---|---|
| `drivers:<regionId>` | Sorted Set (Geo) | `driverId → (lon, lat)` | Dispatch (GEOEARCH), Location Ingestion (GEOADD) | None — evicted on offline |
| `driver:status:<driverId>` | String | `IDLE \| OFFER_SENT \| ON_TRIP \| OFFLINE` | Dispatch | **90 s** — refreshed on every location heartbeat; expiry = driver considered offline |
| `ride:state:<rideId>` | Hash | `status, driverId, offerId, surgeMultiplier` | Dispatch, Trip | 24 h |
| `offer:<offerId>` | String | `rideId:driverId:expiresAt` | Dispatch (offer TTL) | **10 s** |
| `ride:inflight:<idempotencyKey>` | String | `1` | Dispatch (dedup in-flight) | **30 s** |
| `supply:<h3CellId>` | Hash | `count` | Surge Pricing | None (auto-decremented) |
| `demand:<h3CellId>` | Hash | `count` | Surge Pricing | None |
| `surge:<h3CellId>` | Hash | `multiplier, demandCount, supplyCount, computedAt` | Dispatch (read), Surge (write) | **60 s** |
| `trip:surge:<tripId>` | String | `multiplier` (locked at match confirmation) | Trip | 24 h |
| `trip:state:<tripId>` | String | Current FSM state: `MATCHED` / `IN_PROGRESS` / `PAUSED` — **deleted** when trip closes | Trip | None — deleted explicitly on COMPLETED or CANCELLED |
| `trip:location:<tripId>` | Hash | `lat`, `lon`, `updatedAt` — last known driver GPS position during trip | Trip, Location Ingestion | **30 s** |
| `driver:current-trip:<driverId>` | String | `tripId` of the active trip | Trip, Dispatch | None — cleared when trip closes |
| `fare:estimate:<rideId>` | String | JSON fare breakdown cached at ride-request time | Dispatch, Rider App | **10 min** |

---

## Kafka Topic Configuration

| Topic | Partitions | Retention | Key | Consumers |
|---|---|---|---|---|
| `driver.location.updates` | 50 | 1 h | `driverId` | Location Ingestion, Surge Pricing |
| `ride.requested` | 20 | 24 h | `rideId` | Dispatch |
| `dispatch.offer.sent` | 20 | 24 h | `driverId` | Notification |
| `dispatch.match.confirmed` | 20 | 24 h | `rideId` | Trip Lifecycle |
| `dispatch.match.failed` | 20 | 24 h | `rideId` | Notification |
| `trip.started` | 20 | 7 d | `tripId` | Notification |
| `trip.ended` | 20 | 7 d | `tripId` | Payment |
| `payment.initiated` | 10 | 7 d | `paymentId` | Payment (self-retry) |
| `payment.completed` | 10 | 7 d | `paymentId` | Notification, Trip Lifecycle |
| `payment.failed` | 10 | 7 d | `paymentId` | Trip Lifecycle |
| `notification.requested` | 10 | 6 h | `userId` | Notification |

Dead-letter topic for each: suffix `.dlq`

---

## CockroachDB Table Placement

```sql
-- Dispatch offers table (owned by Dispatch Service)
CREATE TABLE dispatch_offers (
    offer_id     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id      UUID         NOT NULL,
    driver_id    UUID         NOT NULL,
    region_id    VARCHAR(32)  NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'SENT'
                              CHECK (status IN ('SENT','ACCEPTED','DECLINED','EXPIRED')),
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_dispatch_offers_ride_id   ON dispatch_offers (ride_id);
CREATE INDEX idx_dispatch_offers_driver_id ON dispatch_offers (driver_id);

-- region-pinned tables: writes stay local
ALTER TABLE ride_requests   CONFIGURE ZONE USING lease_preferences = [[+region=ap-south-1]];
ALTER TABLE dispatch_offers CONFIGURE ZONE USING lease_preferences = [[+region=ap-south-1]];
ALTER TABLE trips           CONFIGURE ZONE USING lease_preferences = [[+region=ap-south-1]];
ALTER TABLE payments        CONFIGURE ZONE USING lease_preferences = [[+region=ap-south-1]];

-- global reference table: replicated everywhere, read anywhere
ALTER TABLE drivers         CONFIGURE ZONE USING num_replicas = 5;
```
