package com.awesome.testing.service;

import com.awesome.testing.dto.order.OrderDto;

/** Result of checkout; {@code replayed} is true when an Idempotency-Key matched an earlier order. */
public record OrderCreation(OrderDto order, boolean replayed) {
}
