package in.parivahan.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka topic: trip.ended
 * Partition key: tripId
 *
 * Includes full fare breakdown so downstream consumers do not need to re-query the DB.
 */
public record TripEndedEvent(
    String      eventId,
    String      tripId,
    String      rideId,
    String      driverId,
    String      riderId,
    String      regionId,       // for Payment Service DB routing
    FarePayload fare,
    double      distanceKm,
    long        durationSec,
    long        pauseDurationSec,
    String      paymentMethod,
    Instant     endedAt
) {

  /** Fare fields duplicated here so this module stays independent of trip-domain. */
  public record FarePayload(
      BigDecimal base,
      BigDecimal distanceCharge,
      BigDecimal timeCharge,
      BigDecimal waitCharge,
      BigDecimal surgeMultiplier,
      BigDecimal subtotal,
      BigDecimal total,
      String currency
  ) {}

  public static TripEndedEvent create(String tripId, String rideId,
                      String driverId, String riderId,
                      String regionId,
                      FarePayload fare,
                      double distanceKm, long durationSec, long pauseDurationSec,
                      String paymentMethod, Instant endedAt) {
    return new TripEndedEvent(
        UUID.randomUUID().toString(),
        tripId, rideId, driverId, riderId,
        regionId,
        fare, distanceKm, durationSec, pauseDurationSec,
        paymentMethod, endedAt
    );
  }
}


