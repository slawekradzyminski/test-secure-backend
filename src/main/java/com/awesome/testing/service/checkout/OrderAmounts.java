package com.awesome.testing.service.checkout;

import com.awesome.testing.entity.OrderEntity;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

/**
 * Derived order amounts. {@code amountDue} is what a card payment must cover:
 * {@code totalAmount} minus the parts paid with gift cards and loyalty points.
 * Until those tracks add their amounts it equals {@code totalAmount}.
 */
@UtilityClass
public class OrderAmounts {

    /** Amount the customer still has to pay; zero when the order has no total. */
    public static BigDecimal amountDue(OrderEntity order) {
        BigDecimal total = order.getTotalAmount();
        return total == null ? BigDecimal.ZERO : total;
    }
}
