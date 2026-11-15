package com.awesome.testing.internal.dto;

import java.util.List;

/**
 * One page of {@code GET /internal/v1/inventory/export}: products in id order with their stock and
 * the movements of the frozen {@code inventory_movements} table. {@code nextAfterProductId} is the
 * {@code afterProductId} of the next page, null on the last one.
 */
public record InventoryExportDto(List<InventoryExportItemDto> items, Long nextAfterProductId) {
}
