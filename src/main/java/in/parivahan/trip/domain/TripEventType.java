package in.parivahan.trip.domain;

/** All FSM transition types recorded in the trip_events audit log. */
public enum TripEventType {
  MATCHED,    // first event, written at trip creation
  STARTED,
  ARRIVED,    // no FSM state change
  PAUSED,
  RESUMED,
  COMPLETED,
  CANCELLED
}
