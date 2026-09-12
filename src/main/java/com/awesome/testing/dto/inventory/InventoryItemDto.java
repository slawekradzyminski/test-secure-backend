package com.awesome.testing.dto.inventory;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemDto {

    private Long productId;
    private String name;
    private String category;
    private Integer availableQuantity;
    private StockStatus stockStatus;
    @Schema(type = "string", format = "local-date-time", description = "Server local date and time without an offset; timezone is deployment-dependent, not an absolute instant")
    private LocalDateTime lastChangedAt;
}
