package in.parivahan.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka topic: payment.failed
 * Partition key: paymentId
 *
 * Emitted by Payment Orchestration Service after all retries are exhausted.
 *
 * Consumed by:
 *  - Trip Lifecycle Service → sets trips.payment_status = FAILED
 */
public record PaymentFailedEvent(
    String     eventId,
    String     paymentId,
    String     tripId,
    String     riderId,
    BigDecimal amount,
    String     currency,
    String     failureReason,   // e.g. PSP_TIMEOUT, INSUFFICIENT_FUNDS, CARD_DECLINED
    int        attemptsExhausted,
    Instant    failedAt
) {
  public static PaymentFailedEvent create(String paymentId, String tripId,
                                          String riderId,
                                          BigDecimal amount, String currency,
                                          String failureReason, int attemptsExhausted) {
    return new PaymentFailedEvent(
        UUID.randomUUID().toString(),
        paymentId, tripId, riderId,
        amount, currency,
        failureReason, attemptsExhausted,
        Instant.now()
    );
  }
}
