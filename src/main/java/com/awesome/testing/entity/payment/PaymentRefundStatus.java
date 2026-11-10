package com.awesome.testing.entity.payment;

/** Delivery state of a card refund in the payment refund outbox. */
public enum PaymentRefundStatus {
    /** Waiting for (another) delivery attempt at {@code nextAttemptAt}. */
    PENDING,
    /** payment-service booked the refund. */
    SENT,
    /** payment-service has no captured payment for the order (404); nothing to refund on the card. */
    SKIPPED,
    /** Rejected (409 exceeds refundable, 400) or out of attempts; needs a human. */
    FAILED
}
