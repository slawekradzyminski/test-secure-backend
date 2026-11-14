package com.awesome.testing.dto.stock;

/** How many pushed levels changed the projection and how many were stale or for unknown products. */
public record StockLevelsResultDto(int applied, int ignored) {
}
