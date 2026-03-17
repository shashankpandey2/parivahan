package in.parivahan.trip.domain;

/**
 * Thrown when a state transition is not permitted for the current trip status.
 */
public class InvalidTripStateException extends RuntimeException {

  public InvalidTripStateException(String message) {
    super(message);
  }
}
