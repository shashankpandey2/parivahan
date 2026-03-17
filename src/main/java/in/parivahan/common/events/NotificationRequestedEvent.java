package in.parivahan.common.events;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Published to Kafka topic: notification.requested
 * Partition key: userId
 *
 * Consumed by: Notification Service.
 */
public record NotificationRequestedEvent(
    String              notificationId,
    String              userId,     // recipient — riderId or driverId
    List<String>        channels,   // e.g. ["PUSH"] or ["PUSH", "SMS"]
    String              template,   // DRIVER_ARRIVED | TRIP_COMPLETED | TRIP_CANCELLED | etc.
    Map<String, String> params,     // template-specific key/value pairs
    Instant             requestedAt
) {
  public static NotificationRequestedEvent create(String userId,
                                                  List<String> channels,
                                                  String template,
                                                  Map<String, String> params) {
    return new NotificationRequestedEvent(
        UUID.randomUUID().toString(),
        userId,
        channels,
        template,
        params,
        Instant.now()
    );
  }
}
