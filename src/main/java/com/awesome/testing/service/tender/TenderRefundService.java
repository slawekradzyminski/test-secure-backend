package com.awesome.testing.service.tender;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.service.giftcard.GiftCardService;
import com.awesome.testing.service.loyalty.LoyaltyPoints;
import com.awesome.testing.service.loyalty.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Gives money back to the gift card and loyalty points an order was paid
 * with: the gift card first, then points, each capped at what it paid minus
 * earlier restores. Every call is idempotent per reference (refund requestId
 * or cancellation). Callers hold the order row lock.
 */
@Service
@RequiredArgsConstructor
public class TenderRefundService {

    private final GiftCardService giftCardService;
    private final LoyaltyService loyaltyService;

    /**
     * Gift card and points legs of a refund: restores {@code amount}, the part of
     * the refund the card payment does not cover. This is the method a
     * {@code RefundSplitter} calls with its non-card remainder; repeating a
     * requestId returns the first result without restoring again.
     */
    @Transactional
    public TenderRefund refundToTenders(OrderEntity order, UUID requestId, BigDecimal amount) {
        return restore(order, amount, refundReference(requestId));
    }

    /**
     * Gift card leg for a {@code RefundLegHandler} of type GIFT_CARD: how much of
     * {@code remaining} can go back to the order's gift card.
     */
    @Transactional(readOnly = true)
    public BigDecimal giftCardRefundable(OrderEntity order, BigDecimal remaining) {
        return giftCardService.restorable(order).min(remaining).max(BigDecimal.ZERO);
    }

    /** Books the gift card leg; idempotent per requestId. */
    @Transactional
    public BigDecimal refundToGiftCard(OrderEntity order, UUID requestId, BigDecimal amount) {
        return giftCardService.restore(order, amount.setScale(2, RoundingMode.HALF_UP), refundReference(requestId));
    }

    /**
     * Points leg for a {@code RefundLegHandler} of type LOYALTY_POINTS: how much
     * of {@code remaining} can go back as points (100 points = 1.00).
     */
    @Transactional(readOnly = true)
    public BigDecimal pointsRefundable(OrderEntity order, BigDecimal remaining) {
        return LoyaltyPoints.valueOf(loyaltyService.restorablePoints(order)).min(remaining).max(BigDecimal.ZERO);
    }

    /** Books the points leg; idempotent per requestId. */
    @Transactional
    public long refundToPoints(OrderEntity order, UUID requestId, BigDecimal amount) {
        return loyaltyService.restoreRedeemed(order, LoyaltyPoints.pointsFor(amount), refundReference(requestId));
    }

    static String refundReference(UUID requestId) {
        return "refund:" + requestId;
    }

    /** Restores everything the order paid with its gift card and points. */
    @Transactional
    public TenderRefund restoreForCancellation(OrderEntity order) {
        BigDecimal paid = orZero(order.getGiftCardAmount()).add(orZero(order.getPointsAmount()));
        return restore(order, paid, cancellationReference(order));
    }

    static String cancellationReference(OrderEntity order) {
        return "cancel:order:" + order.getId();
    }

    private TenderRefund restore(OrderEntity order, BigDecimal amount, String reference) {
        BigDecimal remaining = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal giftCardAmount = giftCardService.restore(order, remaining, reference);
        remaining = remaining.subtract(giftCardAmount);
        long points = loyaltyService.restoreRedeemed(order, LoyaltyPoints.pointsFor(remaining), reference);
        BigDecimal pointsAmount = LoyaltyPoints.valueOf(points);
        return new TenderRefund(giftCardAmount, points, pointsAmount, remaining.subtract(pointsAmount));
    }

    private static BigDecimal orZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
