package in.parivahan.trip.domain;

import java.time.Instant;

/**
 * An immutable record of a single FSM state transition.
 * Persisted to Postgres table: trip_events (append-only audit log).
 */
public class TripEvent {

  private final String        eventId;
  private final String        tripId;
  private final TripEventType eventType;
  private final Double        latitude;    // nullable
  private final Double        longitude;   // nullable
  private final Instant       occurredAt;

  public TripEvent(String eventId, String tripId, TripEventType eventType,
                   Double latitude, Double longitude, Instant occurredAt) {
    this.eventId    = eventId;
    this.tripId     = tripId;
    this.eventType  = eventType;
    this.latitude   = latitude;
    this.longitude  = longitude;
    this.occurredAt = occurredAt;
  }

  public String        getEventId()    { return eventId; }
  public String        getTripId()     { return tripId; }
  public TripEventType getEventType()  { return eventType; }
  public Double        getLatitude()   { return latitude; }
  public Double        getLongitude()  { return longitude; }
  public Instant       getOccurredAt() { return occurredAt; }
}
