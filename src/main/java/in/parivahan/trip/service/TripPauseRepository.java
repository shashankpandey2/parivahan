package in.parivahan.trip.service;

import in.parivahan.trip.domain.TripPause;

import java.time.Instant;
import java.util.List;

/**
 * Port for TripPause persistence.
 * Adapter (CockroachDB / Postgres) is injected by Spring at runtime.
 */
public interface TripPauseRepository {

  /** Persist a new pause row (paused_at set, resumed_at null). */
  void save(TripPause pause);

  /** Return all pause records for the given trip, ordered by paused_at ASC. */
  List<TripPause> findByTripId(String tripId);

  /**
   * Close the most recent open pause (resumed_at = NULL) for this trip.
   * No-op if no open pause exists.
   */
  void closeOpenPauseIfExists(String tripId, Instant resumedAt);
}
