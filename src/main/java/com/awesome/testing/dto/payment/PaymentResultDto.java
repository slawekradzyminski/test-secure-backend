package com.awesome.testing.dto.payment;

import com.awesome.testing.entity.payment.OrderPaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of {@code POST /internal/v1/orders/{orderId}/payment}, sent by payment-service
 * when a payment reaches a final result. Lives in the {@code dto} package so the native
 * image registers it for Jackson binding (the controller parses the raw, signed body).
 *
 * @param currency    defaults to USD when omitted
 * @param failureCode required for {@code FAILED}
 */
public record PaymentResultDto(
        @NotNull(message = "requestId is required") UUID requestId,
        @NotNull(message = "paymentId is required") UUID paymentId,
        @NotNull(message = "status is required") OrderPaymentStatus status,
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.00", message = "amount must not be negative")
        @Digits(integer = 8, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a three-letter ISO code") String currency,
        @Size(max = 64, message = "failureCode must be at most 64 characters") String failureCode) {
}
