package com.awesome.testing.dto.payment;

import com.awesome.testing.dto.order.OrderStatus;

/** The order and its status after a payment result was applied (or replayed). */
public record PaymentResultResponseDto(Long orderId, OrderStatus orderStatus) {
}
