package in.parivahan.common.events;

import in.parivahan.common.domain.GeoPoint;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka topic: driver.location.updates
 * Partition key: driverId
 */
public record DriverLocationEvent(
    String  eventId,
    String  driverId,
    String  regionId,
    GeoPoint location,
    double  heading,    // degrees 0–360
    double  accuracy,   // metres
    String  h3CellId,   // H3 resolution-8 hex cell
    Instant occurredAt
) {
  public static DriverLocationEvent create(String driverId, String regionId,
                      GeoPoint location,
                      double heading, double accuracy,
                      String h3CellId) {
    return new DriverLocationEvent(
        UUID.randomUUID().toString(),
        driverId, regionId,
        location, heading, accuracy, h3CellId,
        Instant.now()
    );
  }
}
