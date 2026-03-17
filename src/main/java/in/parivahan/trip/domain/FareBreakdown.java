package in.parivahan.trip.domain;

import java.math.BigDecimal;

/**
 * Itemised fare breakdown returned as part of trip completion.
 */
public record FareBreakdown(
    BigDecimal base,
    BigDecimal distanceCharge,
    BigDecimal timeCharge,
    BigDecimal waitCharge,
    BigDecimal surgeMultiplier,
    BigDecimal subtotal,
    BigDecimal total,
    String currency
) {}
