package com.awesome.testing.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/** payment-service's answer to a card refund; {@code status} is the payment status after it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentRefundResponseDto(UUID refundId, UUID paymentId, BigDecimal amount, String status) {
}
