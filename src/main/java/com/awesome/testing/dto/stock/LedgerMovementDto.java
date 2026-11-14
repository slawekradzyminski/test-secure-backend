package com.awesome.testing.dto.stock;

import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.entity.inventory.InventoryMovementType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A warehouse ledger movement: the public {@link InventoryMovementDto} plus the product's ledger
 * version after it (only on adjustment replies).
 */
public record LedgerMovementDto(
        Long id,
        Long productId,
        Long orderId,
        InventoryMovementType type,
        Integer delta,
        Integer quantityAfter,
        String actor,
        String reason,
        UUID requestId,
        LocalDateTime createdAt,
        Long ledgerVersion) {

    /** The public movement, without the ledger version. */
    public InventoryMovementDto toMovement() {
        return InventoryMovementDto.builder()
                .id(id)
                .productId(productId)
                .orderId(orderId)
                .type(type)
                .delta(delta)
                .quantityAfter(quantityAfter)
                .actor(actor)
                .reason(reason)
                .requestId(requestId)
                .createdAt(createdAt)
                .build();
    }
}
