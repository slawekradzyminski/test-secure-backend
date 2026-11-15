package com.awesome.testing.internal.dto;

import java.util.List;

/** A product's projected stock quantity and its movements, oldest first. */
public record InventoryExportItemDto(Long productId, Integer quantity, List<InventoryExportMovementDto> movements) {
}
