package com.awesome.testing.service.tender;

import com.awesome.testing.service.loyalty.LoyaltyPoints;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How a total is divided between the gift card, loyalty points and what is
 * left to pay. Tender order: the coupon discount is already in the total, then
 * the gift card, then points; every part is capped so {@code amountDue} never
 * drops below zero.
 *
 * @param giftCardAmount part paid with the gift card, null without a gift card
 * @param pointsRedeemed points used, null without points; below the requested
 *                       points when the remaining total needs fewer
 * @param pointsAmount   value of {@code pointsRedeemed}, null without points
 * @param amountDue      what remains to pay
 */
public record TenderSplit(BigDecimal giftCardAmount, Long pointsRedeemed, BigDecimal pointsAmount, BigDecimal amountDue) {

    /**
     * @param total           order total after the coupon discount
     * @param giftCardBalance usable gift card balance, null when no gift card is applied
     *                        (zero when the card cannot be used)
     * @param points          requested points, null when no points are applied
     */
    public static TenderSplit split(BigDecimal total, BigDecimal giftCardBalance, Long points) {
        BigDecimal remaining = total.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal giftCardAmount = null;
        if (giftCardBalance != null) {
            giftCardAmount = giftCardBalance.max(BigDecimal.ZERO).min(remaining).setScale(2, RoundingMode.HALF_UP);
            remaining = remaining.subtract(giftCardAmount);
        }
        Long pointsRedeemed = null;
        BigDecimal pointsAmount = null;
        if (points != null) {
            pointsRedeemed = Math.min(Math.max(points, 0), LoyaltyPoints.pointsFor(remaining));
            pointsAmount = LoyaltyPoints.valueOf(pointsRedeemed);
            remaining = remaining.subtract(pointsAmount);
        }
        return new TenderSplit(giftCardAmount, pointsRedeemed, pointsAmount, remaining);
    }
}
