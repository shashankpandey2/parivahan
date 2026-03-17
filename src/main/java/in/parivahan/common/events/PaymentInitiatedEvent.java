package in.parivahan.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka topic: payment.initiated
 * Partition key: paymentId
 *
 * Published by: Trip Lifecycle Service (cancellation fees) and Payment Orchestration Service.
 * Consumed by: Payment Orchestration Service.
 */
public record PaymentInitiatedEvent(
    String     eventId,
    String     paymentId,
    String     tripId,
    String     riderId,
    BigDecimal amount,
    String     currency,
    String     paymentMethod,   // UPI | CARD | CASH | WALLET
    String     idempotencyKey,  // prevents double-charge on retry
    Instant    initiatedAt
) {
  public static PaymentInitiatedEvent create(String paymentId, String tripId,
                                             String riderId,
                                             BigDecimal amount, String currency,
                                             String paymentMethod, String idempotencyKey) {
    return new PaymentInitiatedEvent(
        UUID.randomUUID().toString(),
        paymentId, tripId, riderId,
        amount, currency,
        paymentMethod, idempotencyKey,
        Instant.now()
    );
  }
}
