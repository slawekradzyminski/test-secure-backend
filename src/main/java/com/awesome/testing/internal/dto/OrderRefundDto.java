package com.awesome.testing.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** A refund for returned items, booked by warehouse-service; idempotent per requestId. */
public record OrderRefundDto(
        @NotNull(message = "requestId is required") UUID requestId,
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 8, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,
        @NotBlank(message = "reference is required") @Size(max = 255, message = "reference must be at most 255 characters")
        String reference) {
}
