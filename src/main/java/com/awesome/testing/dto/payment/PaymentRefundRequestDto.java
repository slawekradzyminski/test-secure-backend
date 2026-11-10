package com.awesome.testing.dto.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of payment-service's {@code POST /internal/v1/payments/refunds}: the card part of a
 * refund booked by the backend. {@code requestId} is the booked refund's requestId.
 */
public record PaymentRefundRequestDto(UUID requestId, Long orderId, BigDecimal amount, String reason) {
}
