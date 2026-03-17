package in.parivahan.common.events;

import in.parivahan.common.domain.GeoPoint;
import in.parivahan.common.domain.RideTier;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka topic: ride.requested
 * Partition key: rideId
 */
public record RideRequestedEvent(
    String eventId,
    String rideId,
    String riderId,
    String regionId,
    GeoPoint pickup,
    String  pickupAddress,
    GeoPoint dropoff,
    String  dropoffAddress,
    RideTier tier,
    String paymentMethod,
    String idempotencyKey,
    Instant occurredAt
) {
  public static RideRequestedEvent create(String rideId, String riderId, String regionId,
                    GeoPoint pickup, String pickupAddress,
                    GeoPoint dropoff, String dropoffAddress,
                    RideTier tier,
                    String paymentMethod, String idempotencyKey) {
    return new RideRequestedEvent(
        UUID.randomUUID().toString(),
        rideId, riderId, regionId,
        pickup, pickupAddress,
        dropoff, dropoffAddress,
        tier,
        paymentMethod, idempotencyKey,
        Instant.now()
    );
  }
}
