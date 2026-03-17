package in.parivahan.trip.domain;

import in.parivahan.common.domain.GeoPoint;
import in.parivahan.common.domain.RideTier;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Core trip entity.
 * Persisted to Postgres table: trips
 */
public class Trip {

  private final String tripId;
  private final String rideId;
  private final String driverId;
  private final String riderId;
  private final String regionId;          // CockroachDB partition key
  private final RideTier tier;
  private final String paymentMethod;     // UPI | CARD | CASH | WALLET
  private final GeoPoint pickupLocation;
  private final GeoPoint dropoffLocation;
  private final BigDecimal surgeMultiplier;  // locked at match confirmation

  private TripStatus status;
  private final Instant matchedAt;   // when dispatch confirmed the match
  private Instant startedAt;
  private Instant endedAt;
  private double distanceKm;
  private int version;
  private FareBreakdown fareBreakdown;

  private String paymentId;
  private String paymentPspReference;

  public Trip(String tripId, String rideId, String driverId, String riderId,
        String regionId, RideTier tier, String paymentMethod,
        GeoPoint pickupLocation, GeoPoint dropoffLocation, BigDecimal surgeMultiplier) {
    this.tripId           = tripId;
    this.rideId           = rideId;
    this.driverId         = driverId;
    this.riderId          = riderId;
    this.regionId         = regionId;
    this.tier             = tier;
    this.paymentMethod    = paymentMethod;
    this.pickupLocation   = pickupLocation;
    this.dropoffLocation  = dropoffLocation;
    this.surgeMultiplier  = surgeMultiplier;
    this.status           = TripStatus.MATCHED;
    this.matchedAt        = Instant.now();
    this.version          = 0;
  }

  // ── State transitions ──────────────────────────────────────────────────────

  public void start() {
    assertStatus(TripStatus.MATCHED);
    this.status    = TripStatus.IN_PROGRESS;
    this.startedAt = Instant.now();
  }

  public void pause() {
    assertStatus(TripStatus.IN_PROGRESS);
    this.status = TripStatus.PAUSED;
  }

  public void resume() {
    assertStatus(TripStatus.PAUSED);
    this.status = TripStatus.IN_PROGRESS;
  }

  public void complete(double distanceKm, FareBreakdown fareBreakdown, Instant endedAt) {
    if (status != TripStatus.IN_PROGRESS && status != TripStatus.PAUSED) {
      throw new InvalidTripStateException("Cannot complete trip in state: " + status);
    }
    this.distanceKm    = distanceKm;
    this.fareBreakdown = fareBreakdown;
    this.endedAt       = endedAt;
    this.status        = TripStatus.COMPLETED;
  }

  public void cancel(Instant cancelledAt) {
    if (status == TripStatus.COMPLETED || status == TripStatus.CANCELLED) {
      throw new InvalidTripStateException("Cannot cancel trip in state: " + status);
    }
    this.status  = TripStatus.CANCELLED;
    this.endedAt = cancelledAt;
  }

  private void assertStatus(TripStatus required) {
    if (this.status != required) {
      throw new InvalidTripStateException(
          "Expected status " + required + " but was " + status);
    }
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public String getTripId()            { return tripId; }
  public String getRideId()            { return rideId; }
  public String getDriverId()          { return driverId; }
  public String getRiderId()           { return riderId; }
  public String getRegionId()          { return regionId; }
  public RideTier getTier()            { return tier; }
  public String getPaymentMethod()     { return paymentMethod; }
  public GeoPoint getPickupLocation()  { return pickupLocation; }
  public GeoPoint getDropoffLocation() { return dropoffLocation; }
  public BigDecimal getSurgeMultiplier() { return surgeMultiplier; }
  public TripStatus getStatus()        { return status; }
  public Instant getMatchedAt()        { return matchedAt; }
  public Instant getStartedAt()        { return startedAt; }
  public Instant getEndedAt()          { return endedAt; }
  public double getDistanceKm()        { return distanceKm; }
  public int getVersion()              { return version; }

  /** Increments the optimistic-locking version counter. Called by the repository after a successful UPDATE. */
  public void incrementVersion()       { this.version++; }

  /** Full itemised fare breakdown, available once the trip is COMPLETED. */
  public FareBreakdown getFareBreakdown() { return fareBreakdown; }
  /** Convenience accessor for the total fare (most callers only need the total). */
  public BigDecimal getFare()          { return fareBreakdown != null ? fareBreakdown.total() : null; }

  public void recordPaymentCompletion(String paymentId, String pspReference) {
    this.paymentId           = paymentId;
    this.paymentPspReference = pspReference;
  }

  public String getPaymentId()           { return paymentId; }
  public String getPaymentPspReference() { return paymentPspReference; }
}
