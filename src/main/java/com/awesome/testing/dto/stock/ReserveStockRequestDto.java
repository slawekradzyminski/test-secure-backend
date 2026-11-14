package com.awesome.testing.dto.stock;

import java.util.List;
import java.util.UUID;

/** {@code POST /internal/v1/stock/reservations} on warehouse-service; lines are unique per product. */
public record ReserveStockRequestDto(UUID reservationId, String username, List<StockLineDto> lines) {
}
