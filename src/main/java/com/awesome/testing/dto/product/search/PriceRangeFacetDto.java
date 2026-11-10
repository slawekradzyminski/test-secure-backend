package com.awesome.testing.dto.product.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Lowest and highest price of the products the facets cover; both null when none match")
public record PriceRangeFacetDto(
        @Schema(types = {"number", "null"}, example = "9.99") BigDecimal min,
        @Schema(types = {"number", "null"}, example = "1299.00") BigDecimal max) {}
