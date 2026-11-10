package com.awesome.testing.service.tender;

import com.awesome.testing.service.payment.RefundLegHandler;
import com.awesome.testing.service.payment.RefundLegType;
import com.awesome.testing.service.payment.RefundSplitContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Loyalty points leg of a booked refund, after the card and the gift card:
 * gives back redeemed points (100 points = 1.00), up to what the order redeemed
 * minus earlier restores. Idempotent per refund requestId.
 */
@Component
@Order(LoyaltyPointsRefundLegHandler.ORDER)
@RequiredArgsConstructor
public class LoyaltyPointsRefundLegHandler implements RefundLegHandler {

    public static final int ORDER = 200;

    private final TenderRefundService tenderRefundService;

    @Override
    public RefundLegType type() {
        return RefundLegType.LOYALTY_POINTS;
    }

    @Override
    public BigDecimal allocate(RefundSplitContext context, BigDecimal remaining) {
        return tenderRefundService.pointsRefundable(context.order(), remaining);
    }

    @Override
    public void book(RefundSplitContext context, BigDecimal amount) {
        tenderRefundService.refundToPoints(context.order(), context.requestId(), amount);
    }
}
