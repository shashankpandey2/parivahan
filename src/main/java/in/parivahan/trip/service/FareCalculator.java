package in.parivahan.trip.service;

import in.parivahan.common.domain.RideTier;
import in.parivahan.trip.domain.FareBreakdown;
import in.parivahan.trip.domain.Trip;
import in.parivahan.trip.domain.TripPause;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Computes the fare for a completed trip.
 *
 * Formula:
 *   total = (base + distanceKm x perKmRate + movingMin x perMinRate + pauseMin x waitRate)
 *           x surgeMultiplier
 *   subject to tier minimum fare.
 *
 * Rates differ by RideTier — AUTO is cheapest, PREMIUM most expensive.
 *
 * distanceKm and endedAt are passed in explicitly because Trip.getEndedAt()
 * and Trip.getDistanceKm() are only set inside Trip.complete(), which must be
 * called after the fare is calculated.
 */
@Component
public class FareCalculator {

  private static final String CURRENCY = "INR";

  /**
   * Per-tier rate configuration.
   * Values are loaded from the config service in production; these defaults are used as fallback.
   */
  private record RateConfig(
      BigDecimal baseFare,
      BigDecimal perKmRate,
      BigDecimal perMinRate,
      BigDecimal waitRate,
      BigDecimal minFare
  ) {
    static RateConfig forTier(RideTier tier) {
      return switch (tier) {
        case AUTO     -> new RateConfig(
            new BigDecimal("15.00"),  // base
            new BigDecimal("8.00"),   // per km
            new BigDecimal("1.00"),   // per moving min
            new BigDecimal("0.25"),   // per wait min
            new BigDecimal("40.00")   // minimum
        );
        case STANDARD -> new RateConfig(
            new BigDecimal("20.00"),
            new BigDecimal("12.00"),
            new BigDecimal("1.50"),
            new BigDecimal("0.50"),
            new BigDecimal("50.00")
        );
        case PREMIUM  -> new RateConfig(
            new BigDecimal("40.00"),
            new BigDecimal("18.00"),
            new BigDecimal("2.50"),
            new BigDecimal("1.00"),
            new BigDecimal("100.00")
        );
      };
    }
  }

  public FareBreakdown calculate(Trip trip, List<TripPause> pauses,
                   double distanceKm, Instant endedAt) {
    RateConfig rates = RateConfig.forTier(trip.getTier());

    long totalMinutes  = Duration.between(trip.getStartedAt(), endedAt).toMinutes();
    long pauseMinutes  = pauses.stream().mapToLong(TripPause::durationMinutes).sum();
    long movingMinutes = Math.max(0, totalMinutes - pauseMinutes);

    BigDecimal distanceCharge = rates.perKmRate()
        .multiply(BigDecimal.valueOf(distanceKm))
        .setScale(2, RoundingMode.HALF_UP);

    BigDecimal timeCharge = rates.perMinRate()
        .multiply(BigDecimal.valueOf(movingMinutes))
        .setScale(2, RoundingMode.HALF_UP);

    BigDecimal waitCharge = rates.waitRate()
        .multiply(BigDecimal.valueOf(pauseMinutes))
        .setScale(2, RoundingMode.HALF_UP);

    BigDecimal subtotal = rates.baseFare()
        .add(distanceCharge)
        .add(timeCharge)
        .add(waitCharge);

    BigDecimal surge = trip.getSurgeMultiplier();
    BigDecimal total = subtotal.multiply(surge)
        .setScale(0, RoundingMode.HALF_UP); // round to nearest rupee

    // keep min fare scale-0 too so comparison doesn't reintroduce decimals
    total = total.max(rates.minFare().setScale(0, RoundingMode.HALF_UP));

    return new FareBreakdown(
        rates.baseFare(), distanceCharge, timeCharge, waitCharge,
        surge, subtotal, total, CURRENCY
    );
  }
}
