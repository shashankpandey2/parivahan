package in.parivahan.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka topic: payment.completed
 * Partition key: paymentId
 *
 * Consumed by:
 *  - Trip Lifecycle Service → sets trips.payment_status = COMPLETED
 *  - Notification Service   → sends "Payment done" push to rider
 */
public record PaymentCompletedEvent(
    String     eventId,
    String     paymentId,
    String     tripId,
    String     riderId,
    BigDecimal amount,
    String     currency,
    String     pspReference,
    Instant    completedAt
) {
  public static PaymentCompletedEvent create(String paymentId, String tripId,
                                             String riderId,
                                             BigDecimal amount, String currency,
                                             String pspReference) {
    return new PaymentCompletedEvent(
        UUID.randomUUID().toString(),
        paymentId, tripId, riderId,
        amount, currency, pspReference,
        Instant.now()
    );
  }
}
