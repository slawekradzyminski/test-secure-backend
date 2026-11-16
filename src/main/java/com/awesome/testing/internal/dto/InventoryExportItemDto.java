package com.awesome.testing.internal.dto;

/**
 * A product's projected stock quantity. The movement history lives in warehouse-service's ledger
 * only (backend 3.24.0 dropped the frozen {@code inventory_movements} table), so a warehouse-service
 * importing into an empty ledger starts every item from this quantity.
 */
public record InventoryExportItemDto(Long productId, Integer quantity) {
}
