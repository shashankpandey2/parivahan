package in.parivahan.common.events;

import in.parivahan.common.domain.GeoPoint;
import in.parivahan.common.domain.RideTier;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka topic: dispatch.match.confirmed
 * Partition key: rideId
 *
 * Consumed by: Trip Lifecycle Service.
 */
public record MatchConfirmedEvent(
    String   eventId,
    String   rideId,
    String   riderId,
    String   driverId,
    String   tripId,           // pre-allocated by Dispatch
    String   offerId,
    String   regionId,         // for CockroachDB Trip partitioning
    RideTier tier,
    double   surgeMultiplier,  // locked at match time
    String   paymentMethod,    // UPI | CARD | CASH | WALLET
    GeoPoint pickup,
    String   pickupAddress,
    GeoPoint dropoff,
    String   dropoffAddress,
    int      etaMinutes,
    Instant  confirmedAt
) {
  public static MatchConfirmedEvent create(String rideId, String riderId, String driverId,
                     String tripId, String offerId,
                     String regionId, RideTier tier,
                     double surgeMultiplier, String paymentMethod,
                     GeoPoint pickup, String pickupAddress,
                     GeoPoint dropoff, String dropoffAddress,
                     int etaMinutes) {
    return new MatchConfirmedEvent(
        UUID.randomUUID().toString(),
        rideId, riderId, driverId,
        tripId, offerId,
        regionId, tier,
        surgeMultiplier, paymentMethod,
        pickup, pickupAddress,
        dropoff, dropoffAddress,
        etaMinutes,
        Instant.now()
    );
  }
}
