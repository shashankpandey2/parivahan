package in.parivahan.trip.service;

import in.parivahan.trip.domain.PaymentStatus;
import in.parivahan.trip.domain.Trip;

/**
 * Port for Trip persistence.
 * Adapter (CockroachDB / Postgres) is injected by Spring at runtime.
 */
public interface TripRepository {

  /**
   * Persist a new or updated trip row. Uses an UPSERT on trip_id.
   */
  void save(Trip trip);

  /**
   * Load a trip by its ID.
   * @throws jakarta.persistence.EntityNotFoundException if no trip exists with this ID
   */
  Trip findByIdOrThrow(String tripId);

  /**
   * Marks a trip's payment as completed and stores the payment and PSP identifiers.
   */
  void completePayment(String tripId, String paymentId, String pspReference);

  /**
   * Updates payment_status to FAILED or REFUNDED.
   */
  void updatePaymentStatus(String tripId, PaymentStatus paymentStatus);
}
