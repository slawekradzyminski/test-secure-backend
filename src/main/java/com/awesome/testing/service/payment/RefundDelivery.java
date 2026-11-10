package com.awesome.testing.service.payment;

import java.util.UUID;

/** What payment-service did with a card refund. */
public record RefundDelivery(Outcome outcome, UUID refundId, String detail) {

    public enum Outcome {
        /** 2xx: refund booked. */
        REFUNDED,
        /** 404: no captured payment for the order; nothing to refund on the card. */
        NO_CAPTURED_PAYMENT,
        /** 409 (exceeds refundable) or another permanent 4xx; retrying cannot help. */
        REJECTED,
        /** 5xx, I/O error, timeout or an auth/rate-limit 4xx; try again later. */
        RETRY
    }

    public static RefundDelivery refunded(UUID refundId) {
        return new RefundDelivery(Outcome.REFUNDED, refundId, null);
    }

    public static RefundDelivery withOutcome(Outcome outcome, String detail) {
        return new RefundDelivery(outcome, null, detail);
    }
}
