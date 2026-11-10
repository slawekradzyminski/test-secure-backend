package com.awesome.testing.service.checkout;

import com.awesome.testing.entity.OrderEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAmountsTest {

    @Test
    void amountDueIsTheOrderTotalUntilOtherTendersExist() {
        OrderEntity order = OrderEntity.builder().totalAmount(new BigDecimal("80.00"))
                .subtotalAmount(new BigDecimal("100.00")).discountAmount(new BigDecimal("20.00")).build();

        assertThat(OrderAmounts.amountDue(order)).isEqualTo(new BigDecimal("80.00"));
    }

    @Test
    void anOrderWithoutATotalOwesNothing() {
        assertThat(OrderAmounts.amountDue(new OrderEntity())).isEqualTo(BigDecimal.ZERO);
    }
}
