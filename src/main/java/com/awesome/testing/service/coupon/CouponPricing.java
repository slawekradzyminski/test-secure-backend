package com.awesome.testing.service.coupon;

import com.awesome.testing.entity.coupon.CouponType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure discount arithmetic shared by cart previews, checkout, and refunds. */
public final class CouponPricing {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CouponPricing() {
    }

    public static BigDecimal discount(CouponType type, BigDecimal value, BigDecimal subtotal) {
        if (subtotal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        BigDecimal raw = switch (type) {
            case PERCENTAGE -> subtotal.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> value.setScale(2, RoundingMode.HALF_UP);
        };
        return raw.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Refund for returned goods, scaled by the share of the order that was
     * actually paid so coupon discounts are returned proportionally.
     */
    public static BigDecimal proportionalRefund(BigDecimal itemsValue, BigDecimal orderTotal, BigDecimal orderSubtotal) {
        if (orderSubtotal == null || orderSubtotal.signum() <= 0) {
            return itemsValue.setScale(2, RoundingMode.HALF_UP);
        }
        return itemsValue.multiply(orderTotal).divide(orderSubtotal, 2, RoundingMode.HALF_UP);
    }
}
