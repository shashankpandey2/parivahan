package in.parivahan.trip.domain;

import java.time.Instant;

/**
 * A pause interval recorded during a trip.
 * Persisted to Postgres table: trip_pauses
 */
public class TripPause {

  private final String  pauseId;
  private final String  tripId;
  private final Instant pausedAt;
  private final String  reason;   // TRAFFIC | BREAKDOWN | REQUESTED_BY_RIDER | OTHER
  private Instant resumedAt;

  public TripPause(String pauseId, String tripId, Instant pausedAt, String reason) {
    this.pauseId  = pauseId;
    this.tripId   = tripId;
    this.pausedAt = pausedAt;
    this.reason   = reason;
  }

  public void close(Instant resumedAt) {
    if (this.resumedAt != null) {
      throw new IllegalStateException("Pause already closed");
    }
    this.resumedAt = resumedAt;
  }

  public boolean isOpen() {
    return resumedAt == null;
  }

  /** Duration in minutes, or 0 if still open. */
  public long durationMinutes() {
    if (resumedAt == null) return 0L;
    return java.time.Duration.between(pausedAt, resumedAt).toMinutes();
  }

  public String getPauseId()       { return pauseId; }
  public String getTripId()        { return tripId; }
  public Instant getPausedAt()     { return pausedAt; }
  public String getReason()        { return reason; }
  public Instant getResumedAt()    { return resumedAt; }
}
