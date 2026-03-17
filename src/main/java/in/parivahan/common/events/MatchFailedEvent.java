package in.parivahan.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka topic: dispatch.match.failed
 * Partition key: rideId
 *
 * Published by: Dispatch Service when no driver accepts within the 30-second window.
 * Consumed by: Notification Service.
 */
public record MatchFailedEvent(
    String  eventId,
    String  rideId,
    String  riderId,
    String  regionId,
    String  reason,    // e.g. NO_DRIVER_AVAILABLE
    int     attempts,  // offer attempts before giving up
    Instant failedAt
) {
  public static MatchFailedEvent create(String rideId, String riderId,
                                        String regionId, String reason, int attempts) {
    return new MatchFailedEvent(
        UUID.randomUUID().toString(),
        rideId, riderId, regionId,
        reason, attempts,
        Instant.now()
    );
  }
}
