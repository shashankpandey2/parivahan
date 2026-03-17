# D3 — APIs & Events

---

## Part A — REST API Schemas

All APIs:
- Use `Idempotency-Key: <uuid>` header on every mutating request
- Return standard error envelope: `{ "error": { "code": "string", "message": "string" } }`
- All timestamps in ISO 8601 / UTC (`2026-03-16T10:30:00Z`)

---

### Rider APIs

#### POST /v1/rides — Create ride request

**Request headers:**
```
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <jwt>
```

**Request body:**
```json
{
  "pickup": {
    "lat": 12.9716,
    "lon": 77.5946,
    "address": "MG Road, Bengaluru"
  },
  "destination": {
    "lat": 12.9352,
    "lon": 77.6245,
    "address": "Koramangala, Bengaluru"
  },
  "tier": "STANDARD",
  "paymentMethod": "UPI"
}
```

**tier:** `STANDARD | PREMIUM | AUTO`
**paymentMethod:** `UPI | CARD | CASH | WALLET`

**Response 202 Accepted:**
```json
{
  "rideId": "ride_01HZ...",
  "status": "REQUESTED",
  "surgeMultiplier": 1.3,
  "estimatedFare": {
    "min": 95.00,
    "max": 110.00,
    "currency": "INR"
  },
  "createdAt": "2026-03-16T10:30:00Z"
}
```

**Response 409 Conflict** (duplicate idempotency key):
```json
{
  "rideId": "ride_01HZ...",
  "status": "REQUESTED"
}
```

---

#### GET /v1/rides/{rideId} — Poll ride state

**Response 200:**
```json
{
  "rideId": "ride_01HZ...",
  "status": "MATCHED",
  "driver": {
    "driverId": "drv_01HZ...",
    "name": "Ramesh K.",
    "rating": 4.7,
    "vehicleNumber": "KA01AB1234",
    "etaMinutes": 3
  },
  "updatedAt": "2026-03-16T10:30:05Z"
}
```

**status values:** `REQUESTED | MATCHED | IN_PROGRESS | PAUSED | COMPLETED | CANCELLED | NO_DRIVER`

---

#### DELETE /v1/rides/{rideId} — Cancel ride

**Request headers:**
```
Idempotency-Key: <uuid>
Authorization: Bearer <jwt>
```

**Response 200:**
```json
{
  "rideId": "ride_01HZ...",
  "status": "CANCELLED",
  "cancellationFee": 0.00,
  "currency": "INR"
}
```

---

### Driver APIs

#### POST /v1/drivers/location — Location update

**Request headers:**
```
Idempotency-Key: <uuid>
Authorization: Bearer <jwt>
```

**Request body:**
```json
{
  "driverId": "drv_01HZ...",
  "lat": 12.9716,
  "lon": 77.5946,
  "heading": 270.0,
  "accuracy": 5.0,
  "timestamp": "2026-03-16T10:30:00Z"
}
```

**Response 204 No Content**

---

#### PUT /v1/drivers/status — Go online / offline

**Request headers:**
```
Idempotency-Key: <uuid>
Authorization: Bearer <jwt>
```

**Request body:**
```json
{
  "status": "ONLINE"
}
```
**status:** `ONLINE | OFFLINE`

**Response 200:**
```json
{
  "driverId": "drv_01HZ...",
  "status": "ONLINE",
  "updatedAt": "2026-03-16T10:30:00Z"
}
```

---

#### POST /v1/drivers/offers/{offerId}/accept

**Request headers:**
```
Idempotency-Key: <uuid>
Authorization: Bearer <jwt>
```

**Response 200:**
```json
{
  "offerId": "off_01HZ...",
  "rideId": "ride_01HZ...",
  "status": "ACCEPTED",
  "pickup": {
    "lat": 12.9716,
    "lon": 77.5946,
    "address": "MG Road, Bengaluru"
  }
}
```

**Response 409 Conflict** (offer expired):
```json
{
  "error": { "code": "OFFER_EXPIRED", "message": "Offer has already expired" }
}
```

---

#### POST /v1/drivers/offers/{offerId}/decline

**Request headers:**
```
Idempotency-Key: <uuid>
Authorization: Bearer <jwt>
```

**Response 200:**
```json
{
  "offerId": "off_01HZ...",
  "status": "DECLINED"
}
```

---

### Trip APIs

#### GET /v1/trips/{tripId} — Poll trip state

**Response 200:**
```json
{
  "tripId":          "trp_01HZ...",
  "rideId":          "ride_01HZ...",
  "status":          "IN_PROGRESS",
  "tier":            "STANDARD",
  "surgeMultiplier": 1.3,
  "driver": {
    "driverId":      "drv_01HZ...",
    "name":          "Ramesh K.",
    "vehicleNumber": "KA01AB1234"
  },
  "startedAt":  "2026-03-16T10:35:00Z",
  "updatedAt":  "2026-03-16T10:40:00Z"
}
```

**status values:** `MATCHED | IN_PROGRESS | PAUSED | COMPLETED | CANCELLED`

**Response 404** if trip not found.

---

#### POST /v1/trips/{tripId}/start

**Request headers:**
```
Idempotency-Key: <uuid>
Authorization: Bearer <jwt>
```

**Response 200:**
```json
{
  "tripId": "trp_01HZ...",
  "status": "IN_PROGRESS",
  "startedAt": "2026-03-16T10:35:00Z",
  "surgeMultiplier": 1.3
}
```

---

#### POST /v1/trips/{tripId}/arrive

Driver signals they have physically arrived at the pickup point. Does **not** change FSM state — it only triggers the `DRIVER_ARRIVED` push notification to the rider.

**Request headers:**
```
Idempotency-Key: <uuid>
Authorization: Bearer <jwt>
```

**Response 200:**
```json
{
  "tripId":   "trp_01HZ...",
  "status":   "MATCHED",
  "arrivedAt": "2026-03-16T10:33:30Z"
}
```

---

#### POST /v1/trips/{tripId}/pause

**Request headers:**
```
Idempotency-Key: <uuid>
Authorization: Bearer <jwt>
```

**Request body:**
```json
{
  "reason": "TRAFFIC"
}
```

**reason:** `TRAFFIC | BREAKDOWN | REQUESTED_BY_RIDER | OTHER`

**Response 200:**
```json
{
  "tripId":   "trp_01HZ...",
  "status":   "PAUSED",
  "pausedAt": "2026-03-16T10:42:00Z"
}
```

---

#### POST /v1/trips/{tripId}/resume

**Request headers:**
```
Idempotency-Key: <uuid>
Authorization: Bearer <jwt>
```

**Response 200:**
```json
{
  "tripId":    "trp_01HZ...",
  "status":    "IN_PROGRESS",
  "resumedAt": "2026-03-16T10:45:00Z"
}
```

---

#### POST /v1/trips/{tripId}/cancel

**Request headers:**
```
Idempotency-Key: <uuid>
Authorization: Bearer <jwt>
```

**Request body:**
```json
{
  "reason": "RIDER_REQUEST"
}
```

**reason:** `RIDER_REQUEST | DRIVER_REQUEST | SYSTEM`

**Response 200:**
```json
{
  "tripId":          "trp_01HZ...",
  "status":          "CANCELLED",
  "cancellationFee": 0.00,
  "currency":        "INR"
}
```

---

#### POST /v1/trips/{tripId}/end

**Request headers:**
```
Idempotency-Key: <uuid>
Authorization: Bearer <jwt>
```

**Request body:**
```json
{
  "distanceKm": 8.4
}
```

**Response 200:**
```json
{
  "tripId": "trp_01HZ...",
  "status": "COMPLETED",
  "endedAt": "2026-03-16T10:55:00Z",
  "fare": {
    "base": 20.00,
    "distanceCharge": 100.80,
    "timeCharge": 25.50,
    "waitCharge": 1.50,
    "surgeMultiplier": 1.3,
    "subtotal": 147.80,
    "total": 192.00,
    "currency": "INR"
  }
}
```

---

#### GET /v1/trips/{tripId}/fare

**Response 200:** same fare object as above (for in-progress fare estimate or final)

---

#### GET /v1/trips/{tripId}/receipt

Full itemised receipt. Available once trip status is `COMPLETED`.

**Response 200:**
```json
{
  "tripId":     "trp_01HZ...",
  "rideId":     "ride_01HZ...",
  "rider":      { "riderId": "usr_01HZ...", "name": "Ananya P." },
  "driver":     { "driverId": "drv_01HZ...", "name": "Ramesh K.", "vehicleNumber": "KA01AB1234" },
  "pickup":     { "lat": 12.9716, "lon": 77.5946, "address": "MG Road, Bengaluru" },
  "dropoff":    { "lat": 12.9352, "lon": 77.6245, "address": "Koramangala, Bengaluru" },
  "startedAt":  "2026-03-16T10:35:00Z",
  "endedAt":    "2026-03-16T10:55:00Z",
  "distanceKm": 8.4,
  "fare": {
    "base":           20.00,
    "distanceCharge": 100.80,
    "timeCharge":     25.50,
    "waitCharge":     1.50,
    "surgeMultiplier": 1.3,
    "subtotal":       147.80,
    "total":          192.00,
    "currency":       "INR"
  },
  "payment": {
    "paymentId":    "pay_01HZ...",
    "status":       "COMPLETED",
    "method":       "UPI",
    "pspReference": "psp_txn_abc123"
  }
}
```

**Response 404** if trip not found or not yet completed.

---

### Payment APIs

#### POST /v1/payments — Initiate payment

**Request headers:**
```
Idempotency-Key: <uuid>
```

**Request body:**
```json
{
  "tripId": "trp_01HZ...",
  "amount": 196.00,
  "currency": "INR",
  "paymentMethod": "UPI"
}
```

**Response 202 Accepted:**
```json
{
  "paymentId": "pay_01HZ...",
  "status": "PENDING",
  "createdAt": "2026-03-16T10:55:01Z"
}
```

---

#### GET /v1/payments/{paymentId}

**Response 200:**
```json
{
  "paymentId": "pay_01HZ...",
  "tripId": "trp_01HZ...",
  "status": "COMPLETED",
  "amount": 196.00,
  "currency": "INR",
  "pspReference": "psp_txn_abc123",
  "completedAt": "2026-03-16T10:55:04Z"
}
```

**status:** `PENDING | COMPLETED | FAILED | REFUNDED`

---

### Admin APIs

#### PUT /v1/admin/feature-flags/{flagName}

**Request body:**
```json
{
  "enabled": true,
  "rolloutPercentage": 100,
  "description": "Enable new dispatch ranking v2"
}
```

**Response 200:**
```json
{
  "flagName": "dispatch_ranking_v2",
  "enabled": true,
  "updatedAt": "2026-03-16T10:00:00Z"
}
```

---

#### POST /v1/admin/kill-switch/{serviceName}

**Request body:**
```json
{
  "action": "DISABLE",
  "reason": "High error rate on payment service",
  "durationMinutes": 15
}
```

**Response 200:**
```json
{
  "serviceName": "payment-service",
  "action": "DISABLE",
  "effectiveUntil": "2026-03-16T10:15:00Z"
}
```

---

#### GET /v1/admin/services/{serviceName}/health

**FR-8 — Observability.** Returns live health status and upstream dependency checks for any named service. Can be called from ops dashboards or alerting pipelines.

**Response 200:**
```json
{
  "serviceName": "dispatch-service",
  "status":      "UP",
  "region":      "ap-south-1",
  "version":     "1.4.2",
  "checks": {
    "kafka":       "UP",
    "redis":       "UP",
    "cockroachdb":  "UP"
  },
  "checkedAt": "2026-03-16T10:00:00Z"
}
```

**status:** `UP | DEGRADED | DOWN`

**Response 503** when status is `DOWN` (allows load-balancers to detect unhealthy instances).

---

## Part B — Kafka Event Payload Schemas

All events include a common envelope:
```json
{
  "eventId":   "<uuid>",
  "eventType": "<topic-name>",
  "version":   "1",
  "timestamp": "<iso8601>",
  "regionId":  "<string>",
  "payload":   { ... }
}
```

---

### `driver.location.updates`
```json
{
  "driverId":  "drv_01HZ...",
  "lat":       12.9716,
  "lon":       77.5946,
  "heading":   270.0,
  "accuracy":  5.0,
  "h3CellId":  "8865b1a34ffffff",
  "timestamp": "2026-03-16T10:30:00Z",
  "regionId":  "ap-south-1"
}
```
**Partition key:** `driverId`

---

### `ride.requested`
```json
{
  "rideId":          "ride_01HZ...",
  "riderId":         "usr_01HZ...",
  "pickup":          { "lat": 12.9716, "lon": 77.5946 },
  "pickupAddress":   "MG Road, Bengaluru",
  "dropoff":         { "lat": 12.9352, "lon": 77.6245 },
  "dropoffAddress":  "Koramangala, Bengaluru",
  "tier":            "STANDARD",
  "paymentMethod":   "UPI",
  "idempotencyKey":  "550e8400-...",
  "regionId":        "ap-south-1",
  "requestedAt":     "2026-03-16T10:30:00Z"
}
```
**Partition key:** `rideId`

---

### `dispatch.offer.sent`
```json
{
  "offerId":   "off_01HZ...",
  "rideId":    "ride_01HZ...",
  "driverId":  "drv_01HZ...",
  "tier":      "STANDARD",
  "pickup":    { "lat": 12.9716, "lon": 77.5946, "address": "MG Road, Bengaluru" },
  "expiresAt": "2026-03-16T10:30:10Z",
  "sentAt":    "2026-03-16T10:30:00Z"
}
```
**Partition key:** `driverId`

---

### `dispatch.match.confirmed`
```json
{
  "rideId":          "ride_01HZ...",
  "riderId":         "usr_01HZ...",
  "driverId":        "drv_01HZ...",
  "tripId":          "trp_01HZ...",
  "offerId":         "off_01HZ...",
  "regionId":        "ap-south-1",
  "tier":            "STANDARD",
  "surgeMultiplier": 1.3,
  "paymentMethod":   "UPI",
  "pickup":          { "lat": 12.9716, "lon": 77.5946 },
  "pickupAddress":   "MG Road, Bengaluru",
  "dropoff":         { "lat": 12.9352, "lon": 77.6245 },
  "dropoffAddress":  "Koramangala, Bengaluru",
  "etaMinutes":      3,
  "confirmedAt":     "2026-03-16T10:30:07Z"
}
```
**Partition key:** `rideId`

---

### `dispatch.match.failed`
```json
{
  "rideId":    "ride_01HZ...",
  "riderId":   "usr_01HZ...",
  "reason":    "NO_DRIVER_AVAILABLE",
  "attempts":  4,
  "failedAt":  "2026-03-16T10:30:30Z"
}
```
**Partition key:** `rideId`

---

### `trip.started`
```json
{
  "tripId":    "trp_01HZ...",
  "rideId":    "ride_01HZ...",
  "driverId":  "drv_01HZ...",
  "riderId":   "usr_01HZ...",
  "startedAt": "2026-03-16T10:35:00Z"
}
```
**Partition key:** `tripId`

---

### `trip.ended`
```json
{
  "tripId":           "trp_01HZ...",
  "rideId":           "ride_01HZ...",
  "driverId":         "drv_01HZ...",
  "riderId":          "usr_01HZ...",
  "regionId":         "ap-south-1",
  "endedAt":          "2026-03-16T10:55:00Z",
  "distanceKm":       8.4,
  "durationSec":      1200,
  "pauseDurationSec": 180,
  "paymentMethod":    "UPI",
  "fare": {
    "base":           20.00,
    "distanceCharge": 100.80,
    "timeCharge":     25.50,
    "waitCharge":     1.50,
    "surgeMultiplier": 1.3,
    "subtotal":       147.80,
    "total":          192.00,
    "currency":       "INR"
  }
}
```
**Partition key:** `tripId`

---

### `payment.initiated`
```json
{
  "paymentId":      "pay_01HZ...",
  "tripId":         "trp_01HZ...",
  "riderId":        "usr_01HZ...",
  "amount":         196.00,
  "currency":       "INR",
  "paymentMethod":  "UPI",
  "idempotencyKey": "550e8400-...",
  "initiatedAt":    "2026-03-16T10:55:01Z"
}
```
**Partition key:** `paymentId`

---

### `payment.completed`
```json
{
  "paymentId":    "pay_01HZ...",
  "tripId":       "trp_01HZ...",
  "riderId":      "usr_01HZ...",
  "status":       "COMPLETED",
  "pspReference": "psp_txn_abc123",
  "amount":       196.00,
  "currency":     "INR",
  "completedAt":  "2026-03-16T10:55:04Z"
}
```
**Partition key:** `paymentId`

---

### `payment.failed`
```json
{
  "paymentId":         "pay_01HZ...",
  "tripId":            "trp_01HZ...",
  "riderId":           "usr_01HZ...",
  "amount":            196.00,
  "currency":          "INR",
  "failureReason":     "PSP_TIMEOUT",
  "attemptsExhausted": 3,
  "failedAt":          "2026-03-16T11:10:00Z"
}
```
**Partition key:** `paymentId`

---

### `notification.requested`
```json
{
  "notificationId": "ntf_01HZ...",
  "userId":         "usr_01HZ...",
  "channels":       ["PUSH", "SMS"],
  "template":       "RIDE_MATCHED",
  "params": {
    "tripId":      "trp_01HZ...",
    "driverId":    "drv_01HZ...",
    "etaMinutes":  "3"
  },
  "requestedAt": "2026-03-16T10:30:07Z"
}
```

**template values:**

| Template | When sent | Trigger |
|---|---|---|
| `RIDE_MATCHED` | Driver accepted; ETA known | `dispatch.match.confirmed` |
| `DRIVER_ARRIVED` | Driver physically at pickup | `POST /trips/{id}/arrive` |
| `TRIP_STARTED` | Driver has started driving | `POST /trips/{id}/start` |
| `TRIP_COMPLETED` | Trip ended normally | `POST /trips/{id}/end` |
| `PAYMENT_DONE` | Payment confirmed by PSP | `payment.completed` |
| `NO_DRIVER_AVAILABLE` | No driver accepted in 30 s | `dispatch.match.failed` |
| `RIDE_CANCELLED` | Rider cancels before any driver is matched (ride still in `REQUESTED` state) | `DELETE /v1/rides/{id}` |
| `TRIP_CANCELLED` | Ride is cancelled after a driver was matched (`MATCHED`, `IN_PROGRESS`, or `PAUSED`) | `POST /trips/{id}/cancel` |

**Partition key:** `userId`
