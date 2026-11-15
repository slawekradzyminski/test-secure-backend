package com.awesome.testing.internal.dto;

/** {@code GET /internal/v1/orders/by-reservation/{id}}: the order a stock reservation belongs to. */
public record OrderReferenceDto(Long orderId) {
}
