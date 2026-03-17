package in.parivahan.trip.service;

import in.parivahan.trip.domain.TripEvent;

/**
 * Port for TripEvent persistence (append-only).
 * Adapter (CockroachDB / Postgres) is injected by Spring at runtime.
 */
public interface TripEventRepository {

  /**
   * Append a new FSM transition event to the trip_events table.
   * Never updates — trip_events is an immutable audit log.
   */
  void save(TripEvent event);
}
