# Parivahan — Multi-Region Ride-Hailing Platform

Design Assignment (DAW) — Java implementation.

---

## Problem Statement (verbatim summary)

Design a **multi-tenant, multi-region** ride-hailing system (Uber/Ola scale) that handles:

- Driver–rider matching with strict latency requirements
- Dynamic surge pricing
- Trip lifecycle management
- Payments at scale

---

## Functional Requirements

| # | Requirement |
|---|---|
| FR-1 | Real-time driver location ingestion — each online driver sends 1–2 updates/second |
| FR-2 | Ride request flow: pickup, destination, tier, payment method |
| FR-3 | Dispatch / Matching: assign driver within **< 1 s p95**; reassign on decline / timeout |
| FR-4 | Dynamic surge pricing: per geo-cell, based on supply–demand |
| FR-5 | Trip lifecycle: start, pause, end, fare calculation, receipts |
| FR-6 | Payments orchestration: external PSPs, retries, reconciliation |
| FR-7 | Notifications: push / SMS for key ride states |
| FR-8 | Admin / ops tooling: feature flags, kill-switches, observability |

---

## Non-Functional Requirements

| Metric | Target |
|---|---|
| Dispatch decision latency | p95 ≤ 1 s |
| End-to-end request → acceptance | p95 ≤ 3 s |
| Dispatch API availability | 99.95 % |
| Concurrent drivers (global) | 300 k |
| Ride requests (peak) | 60 k / min |
| Location updates (global) | 500 k / s |
| Deployment | Multi-region — region-local writes, no cross-region sync on hot path, failover handling |
| Compliance | PCI, PII encryption, GDPR / DPDP |

---

## Constraints (mandatory)

- **Events backbone:** Kafka / Pulsar
- **Hot KV store:** Redis
- **Transactional DB:** Postgres / CockroachDB
- **All APIs must be idempotent** — mobile clients on flaky networks
- **Payments via external PSPs** — their latency is outside our control

---

## Deliverables — Exactly 5 (from problem statement)

```
1. HLD            — components, data flow, scaling, storage, trade-offs
2. LLD            — deep dive into ONE of: Dispatch/Matching | Surge Pricing | Trip Service
3. APIs & Events  — request/response schemas and event topics
4. Data Model     — ERD for the chosen LLD component
5. Resilience     — retries, backpressure, circuit breakers, failure modes
```

---

## Deliverables Checklist

### D1 — High-Level Design (HLD) ✅

| File | Contents |
|---|---|
| [docs/hld/components.md](docs/hld/components.md) | System context diagram, 8-service responsibility table, dependency graph |
| [docs/hld/data-flow.md](docs/hld/data-flow.md) | 6 sequence diagrams: driver online, heartbeat, dispatch, trip lifecycle, payment, region failover |
| [docs/hld/scaling.md](docs/hld/scaling.md) | Kafka partition math, per-service scaling strategy, auto-scaling triggers |
| [docs/hld/storage.md](docs/hld/storage.md) | Redis key catalogue, Kafka topic config, CockroachDB region placement |
| [docs/hld/multi-region.md](docs/hld/multi-region.md) | 3-region topology diagram, failover sequence (T+0 → T+60s), active-active decisions |
| [docs/hld/trade-offs.md](docs/hld/trade-offs.md) | 10-row decision log: Redis vs PostGIS, CockroachDB vs replicas, async vs sync, etc. |

---

### D2 — Low-Level Design (LLD) — Trip Service ✅

| File | Contents |
|---|---|
| [docs/lld/trip/fsm.md](docs/lld/trip/fsm.md) | Trip state machine (MATCHED/IN_PROGRESS/PAUSED/COMPLETED/CANCELLED), full transition table, invalid-transition guard |
| [docs/lld/trip/fare-calculation.md](docs/lld/trip/fare-calculation.md) | Fare formula, rate config table, worked example (₹192), surge multiplier locked at match confirmation |
| [docs/lld/trip/sequence-diagrams.md](docs/lld/trip/sequence-diagrams.md) | Happy path, pause/resume, invalid state guard, FSM decision flow |

---

### D3 — APIs & Events ✅ → [docs/apis.md](docs/apis.md)

#### REST APIs
- [x] Rider: create ride request (idempotency-key), poll state, cancel
- [x] Driver: location update (idempotent), go online/offline, accept offer, decline offer
- [x] Trip: start, end, get fare
- [x] Payment: initiate (idempotent), get status
- [x] Admin: toggle feature flag, trigger kill-switch
- [x] Request / response schemas for each endpoint

#### Kafka Event Topics
- [x] `driver.location.updates`
- [x] `ride.requested`
- [x] `dispatch.offer.sent`
- [x] `dispatch.match.confirmed`
- [x] `dispatch.match.failed`
- [x] `trip.started`
- [x] `trip.ended`
- [x] `payment.initiated`
- [x] `payment.completed`
- [x] `payment.failed`
- [x] `notification.requested`
- [x] Payload schema (fields and types) for each topic

---

### D4 — Data Model / ERD ✅ → [docs/erd/trip.md](docs/erd/trip.md)

Mermaid `erDiagram` for the Trip Service: entities `TRIPS`, `TRIP_EVENTS`, `TRIP_PAUSES`, `RIDE_REQUESTS` with cardinalities, annotated SQL schema, and Redis key layout.

---

### D5 — Resilience Plan ✅ → [docs/resilience.md](docs/resilience.md)

- [x] **Retries** — exponential backoff with jitter; DLQ after 3 failures for all Kafka consumers
- [x] **Backpressure** — Kafka consumer lag monitoring; rate limiting on location ingest; connection pool limits
- [x] **Circuit breakers** — on PSP calls; on cross-service calls; states + cooldown config
- [x] **Timeout budget** — breakdown of the 3s end-to-end p95 target per hop
- [x] **Failure modes & mitigations** — PSP timeout, no driver, Redis unavailable, Kafka outage, region partition, dual-write gap (outbox pattern)

---

## Compliance (NFR)

| Requirement | Approach |
|---|---|
| **PCI DSS** | Raw card data never enters our storage — PSP hosted fields + tokenisation. Scope reduced to SAQ A. |
| **PII encryption** | Column-level AES-256 on rider/driver name, phone, address fields in CockroachDB; keys in cloud KMS. |
| **GDPR / DPDP right to erasure** | Pseudonymisation on erasure request — PII columns replaced with `[DELETED]`. Financial records retained 7 years with PII stripped. |

Full decisions documented in [docs/hld/trade-offs.md](docs/hld/trade-offs.md) (decisions #12–14).

---

## Java Code

Code covers `common-lib` (shared domain models + Kafka event schemas) and the Trip Service.

### common-lib (`src/main/java/in/parivahan/common/`)

| File | Purpose |
|---|---|
| `domain/GeoPoint.java` | GPS coordinate value object with range validation |
| `domain/RideTier.java` | AUTO / STANDARD / PREMIUM enum with `canServe()` compatibility check |
| `events/RideRequestedEvent.java` | Kafka event: ride.requested (includes pickupAddress + dropoffAddress) |
| `events/MatchConfirmedEvent.java` | Kafka event: dispatch.match.confirmed (regionId, tier, surge, pickup/dropoff forwarded from ride.requested) |
| `events/DriverLocationEvent.java` | Kafka event: driver.location.updates |
| `events/TripStartedEvent.java` | Kafka event: trip.started |
| `events/TripEndedEvent.java` | Kafka event: trip.ended (regionId, full fare breakdown, `create()` factory) |
| `events/PaymentCompletedEvent.java` | Kafka event: payment.completed (consumed by Trip Lifecycle to set payment_status = COMPLETED) |
| `events/PaymentFailedEvent.java` | Kafka event: payment.failed (consumed by Trip Lifecycle to set payment_status = FAILED) |

### Trip Service (`src/main/java/in/parivahan/trip/`)

| File | Purpose |
|---|---|
| `domain/TripStatus.java` | MATCHED / IN_PROGRESS / PAUSED / COMPLETED / CANCELLED |
| `domain/Trip.java` | Trip aggregate root — FSM transitions, holds regionId + tier + full FareBreakdown |
| `domain/TripPause.java` | Pause interval with duration calculation |
| `domain/TripEvent.java` | Immutable FSM audit-log record (maps to trip_events table) |
| `domain/TripEventType.java` | MATCHED / STARTED / ARRIVED / PAUSED / RESUMED / COMPLETED / CANCELLED enum |
| `domain/FareBreakdown.java` | Itemised fare record (base, distance, time, wait, surge) |
| `domain/InvalidTripStateException.java` | Thrown on illegal state transition attempt |
| `service/FareCalculator.java` | Tier-based fare computation (AUTO / STANDARD / PREMIUM rate configs) |
| `service/TripLifecycleService.java` | Orchestrates FSM transitions; Redis state/surge keys; trip_events audit writes; cancellation fee |
| `service/TripRepository.java` | Port interface for Trip persistence (CockroachDB adapter injected at runtime) |
| `service/TripPauseRepository.java` | Port interface for TripPause persistence |
| `service/TripEventRepository.java` | Port interface for TripEvent persistence (append-only) |

