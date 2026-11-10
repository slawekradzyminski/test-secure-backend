package com.awesome.testing.service.tender;

import com.awesome.testing.service.payment.RefundLegHandler;
import com.awesome.testing.service.payment.RefundLegType;
import com.awesome.testing.service.payment.RefundSplitContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Gift card leg of a booked refund: after the card, the refund goes back to the
 * gift card the order was paid with, up to what it paid minus earlier restores.
 * Idempotent per refund requestId.
 */
@Component
@Order(GiftCardRefundLegHandler.ORDER)
@RequiredArgsConstructor
public class GiftCardRefundLegHandler implements RefundLegHandler {

    /** Before {@link LoyaltyPointsRefundLegHandler#ORDER}. */
    public static final int ORDER = 100;

    private final TenderRefundService tenderRefundService;

    @Override
    public RefundLegType type() {
        return RefundLegType.GIFT_CARD;
    }

    @Override
    public BigDecimal allocate(RefundSplitContext context, BigDecimal remaining) {
        return tenderRefundService.giftCardRefundable(context.order(), remaining);
    }

    @Override
    public void book(RefundSplitContext context, BigDecimal amount) {
        tenderRefundService.refundToGiftCard(context.order(), context.requestId(), amount);
    }
}
