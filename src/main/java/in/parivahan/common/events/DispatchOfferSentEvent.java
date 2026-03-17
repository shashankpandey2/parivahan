package in.parivahan.common.events;

import in.parivahan.common.domain.GeoPoint;
import in.parivahan.common.domain.RideTier;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka topic: dispatch.offer.sent
 * Partition key: driverId
 *
 * Published by: Dispatch Service.
 * Consumed by: Notification Service.
 */
public record DispatchOfferSentEvent(
    String   eventId,
    String   offerId,
    String   rideId,
    String   driverId,
    RideTier tier,
    GeoPoint pickup,
    String   pickupAddress,
    Instant  expiresAt,
    Instant  sentAt
) {
  public static DispatchOfferSentEvent create(String offerId, String rideId,
                                              String driverId, RideTier tier,
                                              GeoPoint pickup, String pickupAddress,
                                              Instant expiresAt) {
    return new DispatchOfferSentEvent(
        UUID.randomUUID().toString(),
        offerId, rideId, driverId, tier,
        pickup, pickupAddress,
        expiresAt,
        Instant.now()
    );
  }
}
