package com.awesome.testing.service.payment;

import java.math.BigDecimal;
import java.util.List;

/**
 * How a booked refund is divided.
 *
 * @param card           the part sent back to the card through payment-service (zero for none)
 * @param additionalLegs the other parts in booking order (gift card, loyalty points)
 */
public record RefundSplit(BigDecimal card, List<RefundLeg> additionalLegs) {

    public RefundSplit {
        additionalLegs = List.copyOf(additionalLegs);
    }

    public static RefundSplit cardOnly(BigDecimal card) {
        return new RefundSplit(card, List.of());
    }

    /** Card plus every additional leg. */
    public BigDecimal allocated() {
        return additionalLegs.stream().map(RefundLeg::amount).reduce(card, BigDecimal::add);
    }
}
