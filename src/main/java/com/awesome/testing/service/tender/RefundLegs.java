package com.awesome.testing.service.tender;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.service.checkout.OrderAmounts;

import java.math.BigDecimal;

/**
 * Default split of a booked refund: the card first, up to what the card paid
 * ({@code amountDue}) minus earlier refunds, which also went to the card first;
 * the rest goes to the tenders (gift card, then points).
 *
 * @param card    part to refund to the card payment
 * @param tenders part for {@link TenderRefundService#refundToTenders}
 */
public record RefundLegs(BigDecimal card, BigDecimal tenders) {

    /**
     * @param refundedBefore refunds of the order booked before this one
     * @param amount         this refund
     */
    public static RefundLegs split(OrderEntity order, BigDecimal refundedBefore, BigDecimal amount) {
        BigDecimal cardRefundable = OrderAmounts.amountDue(order).subtract(refundedBefore).max(BigDecimal.ZERO);
        BigDecimal card = amount.min(cardRefundable);
        return new RefundLegs(card, amount.subtract(card));
    }
}
