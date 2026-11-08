package com.awesome.testing.internal.dto;

import com.awesome.testing.entity.inventory.InventoryMovementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Stock received by the warehouse: a purchase receipt or a resellable return. */
public record InventoryReceiptDto(
        @NotNull(message = "requestId is required") UUID requestId,
        @NotNull(message = "type is required") InventoryMovementType type,
        Long orderId,
        @NotBlank(message = "reference is required") @Size(max = 255, message = "reference must be at most 255 characters")
        String reference,
        @NotEmpty(message = "lines must not be empty") @Size(max = 100, message = "at most 100 lines are allowed")
        List<@Valid @NotNull InventoryReceiptLineDto> lines) {
}
