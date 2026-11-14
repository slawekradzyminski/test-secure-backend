package com.awesome.testing.internal.dto;

import com.awesome.testing.entity.inventory.InventoryMovementEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;

import java.time.LocalDateTime;
import java.util.UUID;

/** A movement of the frozen backend ledger, with its id kept for warehouse-service's import. */
public record InventoryExportMovementDto(
        Long id,
        Long orderId,
        InventoryMovementType type,
        Integer delta,
        Integer quantityAfter,
        String actor,
        String reason,
        UUID requestId,
        LocalDateTime createdAt) {

    public static InventoryExportMovementDto from(InventoryMovementEntity movement) {
        return new InventoryExportMovementDto(
                movement.getId(),
                movement.getOrder() == null ? null : movement.getOrder().getId(),
                movement.getType(),
                movement.getDelta(),
                movement.getQuantityAfter(),
                movement.getActor(),
                movement.getReason(),
                movement.getRequestId(),
                movement.getCreatedAt());
    }
}
