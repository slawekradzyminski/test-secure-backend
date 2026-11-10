package com.awesome.testing.service.payment;

/** Where a non-card part of a refund goes; booked by the {@link RefundLegHandler} of that type. */
public enum RefundLegType {
    GIFT_CARD,
    LOYALTY_POINTS
}
