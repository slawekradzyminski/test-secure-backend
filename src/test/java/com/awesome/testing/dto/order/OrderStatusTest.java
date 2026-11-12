package com.awesome.testing.dto.order;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "PENDING, PENDING, false",
            "PENDING, PAID, true",
            "PENDING, SHIPPED, true",
            "PENDING, DELIVERED, true",
            "PENDING, CANCELLED, true",
            "PAID, PENDING, false",
            "PAID, PAID, false",
            "PAID, SHIPPED, true",
            "PAID, DELIVERED, true",
            "PAID, CANCELLED, true",
            "SHIPPED, PENDING, false",
            "SHIPPED, PAID, false",
            "SHIPPED, SHIPPED, false",
            "SHIPPED, DELIVERED, true",
            "SHIPPED, CANCELLED, false",
            "DELIVERED, PENDING, false",
            "DELIVERED, PAID, false",
            "DELIVERED, SHIPPED, false",
            "DELIVERED, DELIVERED, false",
            "DELIVERED, CANCELLED, false",
            "CANCELLED, PENDING, false",
            "CANCELLED, PAID, false",
            "CANCELLED, SHIPPED, false",
            "CANCELLED, DELIVERED, false",
            "CANCELLED, CANCELLED, false"
    })
    void statusesOnlyMoveForwardAndTerminalStatusesStay(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }
}
