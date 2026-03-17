package in.parivahan.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka topic: trip.started
 * Partition key: tripId
 *
 * Consumed by: Notification Service.
 */
public record TripStartedEvent(
    String  eventId,
    String  tripId,
    String  rideId,
    String  driverId,
    String  riderId,
    Instant startedAt
) {
  public static TripStartedEvent create(String tripId, String rideId,
                    String driverId, String riderId,
                    Instant startedAt) {
    return new TripStartedEvent(
        UUID.randomUUID().toString(),
        tripId, rideId, driverId, riderId,
        startedAt
    );
  }
}
