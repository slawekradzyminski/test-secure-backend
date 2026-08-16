package com.awesome.testing.dto.inventory;

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
    private LocalDateTime lastChangedAt;
}
