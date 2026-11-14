package com.awesome.testing.dto.stock;

/** One product and quantity of a warehouse stock reservation or restore. */
public record StockLineDto(Long productId, Integer quantity) {
}
