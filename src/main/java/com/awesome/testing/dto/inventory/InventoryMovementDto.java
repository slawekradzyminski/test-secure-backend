package com.awesome.testing.dto.inventory;

import com.awesome.testing.entity.inventory.InventoryMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovementDto {

    private Long id;
    private Long productId;
    private Long orderId;
    private InventoryMovementType type;
    private Integer delta;
    private Integer quantityAfter;
    private String actor;
    private String reason;
    private UUID requestId;
    private LocalDateTime createdAt;
}
