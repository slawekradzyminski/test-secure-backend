package com.awesome.testing.service.loyalty;

import com.awesome.testing.dto.loyalty.LoyaltyTier;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Loyalty arithmetic. Customers earn 1 point per full 1.00 of a paid order's
 * {@code totalAmount} and redeem points in whole multiples of 100, where 100
 * points are worth 1.00 (one point is one cent).
 */
@UtilityClass
public class LoyaltyPoints {

    /** Smallest redeemable amount and the step between redeemable amounts. */
    public static final long REDEEM_STEP = 100;

    static final long SILVER_FROM = 1_000;
    static final long GOLD_FROM = 5_000;

    /** Points earned for an order total: one per full 1.00, never negative. */
    public static long earnedFor(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.signum() <= 0) {
            return 0;
        }
        return totalAmount.setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /** Money value of points: 100 points = 1.00. */
    public static BigDecimal valueOf(long points) {
        return BigDecimal.valueOf(points).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Points worth exactly {@code amount}; the amount has at most two decimals. */
    public static long pointsFor(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    public static boolean isRedeemable(long points) {
        return points >= REDEEM_STEP && points % REDEEM_STEP == 0;
    }

    /**
     * Earned points a refund takes back, as a running total: the share of
     * {@code earned} that {@code refundedSoFar} is of {@code totalAmount},
     * rounded down so the customer never loses more than the refunded share.
     */
    public static long revocableFor(long earned, BigDecimal totalAmount, BigDecimal refundedSoFar) {
        if (earned <= 0 || totalAmount == null || totalAmount.signum() <= 0 || refundedSoFar == null
                || refundedSoFar.signum() <= 0) {
            return 0;
        }
        if (refundedSoFar.compareTo(totalAmount) >= 0) {
            return earned;
        }
        return BigDecimal.valueOf(earned).multiply(refundedSoFar)
                .divide(totalAmount, 0, RoundingMode.FLOOR)
                .longValueExact();
    }

    public static LoyaltyTier tierFor(long lifetimeEarned) {
        if (lifetimeEarned >= GOLD_FROM) {
            return LoyaltyTier.GOLD;
        }
        if (lifetimeEarned >= SILVER_FROM) {
            return LoyaltyTier.SILVER;
        }
        return LoyaltyTier.BRONZE;
    }
}
