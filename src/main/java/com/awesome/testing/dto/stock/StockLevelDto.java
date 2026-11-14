package com.awesome.testing.dto.stock;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A product's quantity in the warehouse ledger at {@code ledgerVersion}: a stock item reply
 * ({@code PUT/GET /internal/v1/stock/items/{id}}) and one entry of a level push
 * ({@code POST /internal/v1/inventory/levels}).
 */
public record StockLevelDto(
        @NotNull @Min(1) Long productId,
        @NotNull @Min(0) Integer quantity,
        @NotNull @Min(1) Long ledgerVersion) {
}
