package com.awesome.testing.dto.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code POST /internal/v1/inventory/levels} from warehouse-service. */
public record StockLevelsDto(@NotNull @Size(max = StockLevelsDto.MAX_LEVELS) List<@NotNull @Valid StockLevelDto> levels) {

    public static final int MAX_LEVELS = 1000;
}
