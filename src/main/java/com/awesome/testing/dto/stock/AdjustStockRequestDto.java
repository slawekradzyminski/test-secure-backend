package com.awesome.testing.dto.stock;

import java.util.UUID;

/** {@code POST /internal/v1/stock/adjustments}: an admin adjustment of the warehouse ledger. */
public record AdjustStockRequestDto(Long productId, Integer delta, String reason, UUID requestId, String actor) {
}
