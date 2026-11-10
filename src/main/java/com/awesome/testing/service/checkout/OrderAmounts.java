package com.awesome.testing.service.checkout;

import com.awesome.testing.entity.OrderEntity;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

/**
 * Derived order amounts. {@code amountDue} is what a card payment must cover:
 * {@code totalAmount} minus the parts paid with a gift card and loyalty points.
 */
@UtilityClass
public class OrderAmounts {

    /** Amount the customer still has to pay; zero when the order has no total. */
    public static BigDecimal amountDue(OrderEntity order) {
        BigDecimal total = order.getTotalAmount();
        if (total == null) {
            return BigDecimal.ZERO;
        }
        return total.subtract(orZero(order.getGiftCardAmount())).subtract(orZero(order.getPointsAmount()));
    }

    /** True when part of the order was paid with a gift card or loyalty points. */
    public static boolean hasTenders(OrderEntity order) {
        return order.getGiftCardAmount() != null || order.getPointsAmount() != null;
    }

    private static BigDecimal orZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
