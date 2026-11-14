package com.awesome.testing.dto.stock;

import java.util.UUID;

/** {@code PUT /internal/v1/stock/items/{productId}}: creates the stock item of a new product. */
public record CreateStockItemRequestDto(Integer initialQuantity, String actor, UUID requestId) {
}
