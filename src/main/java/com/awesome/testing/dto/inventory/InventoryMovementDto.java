package com.awesome.testing.dto.inventory;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(types = {"integer", "null"})
    private Long orderId;
    private InventoryMovementType type;
    private Integer delta;
    private Integer quantityAfter;
    private String actor;
    private String reason;
    @Schema(types = {"string", "null"})
    private UUID requestId;
    @Schema(type = "string", format = "local-date-time", description = "Server local date and time without an offset; timezone is deployment-dependent, not an absolute instant")
    private LocalDateTime createdAt;
}
