package com.awesome.testing.service.payment;

import java.math.BigDecimal;

/**
 * Takes back the part of a refund that does not go to the card. Beans run in
 * {@link org.springframework.core.annotation.Order} after the card leg, each seeing what the
 * previous ones left; both methods run inside the transaction that booked the refund, so
 * throwing rolls the refund back.
 */
public interface RefundLegHandler {

    RefundLegType type();

    /** How much of {@code remaining} (positive) this leg takes back; values are clamped to 0..remaining. */
    BigDecimal allocate(RefundSplitContext context, BigDecimal remaining);

    /** Books the leg chosen by {@link #allocate}; {@code amount} is positive. */
    void book(RefundSplitContext context, BigDecimal amount);
}
