package com.awesome.testing.dto.product.search;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Number of matching products in one category")
public record CategoryFacetDto(
        @Schema(description = "Category as stored on the products", example = "Electronics") String value,
        @Schema(description = "Matching products in this category", example = "7") long count) {}
