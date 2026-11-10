package com.awesome.testing.service.payment;

import com.awesome.testing.entity.OrderEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A refund that was just booked, as seen by {@link RefundSplitter} and {@link RefundLegHandler}s.
 *
 * @param order          the refunded order, locked by the booking transaction
 * @param amount         the booked refund amount
 * @param cardRefundable what can still go back to the card: the captured payment's amount minus
 *                       the card refunds already sent or queued; zero when the order has no
 *                       captured payment (for example an order marked PAID by an admin)
 * @param requestId      the booked refund's requestId (also the card refund's requestId)
 * @param reference      the booked refund's reference, sent to payment-service as the reason
 */
public record RefundSplitContext(
        OrderEntity order,
        BigDecimal amount,
        BigDecimal cardRefundable,
        UUID requestId,
        String reference) {
}
