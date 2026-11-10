package com.awesome.testing.dto.product.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Facets over every filter except category, so a client can offer the other categories")
public record ProductSearchFacetsDto(
        @Schema(description = "Categories with their product counts, largest first, then by value")
        List<CategoryFacetDto> categories,
        PriceRangeFacetDto priceRange,
        @Schema(description = "Products with stock above zero", example = "18") long inStockCount) {}
