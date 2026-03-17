package in.parivahan.trip.domain;

/** Trip FSM states. */
public enum TripStatus {
  MATCHED,
  IN_PROGRESS,
  PAUSED,
  COMPLETED,
  CANCELLED
}
