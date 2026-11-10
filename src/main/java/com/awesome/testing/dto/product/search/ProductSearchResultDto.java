package com.awesome.testing.dto.product.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "One page of product search hits with facets")
public record ProductSearchResultDto(
        List<ProductSearchItemDto> content,
        @Schema(description = "Zero-based page index", example = "0") int pageNumber,
        @Schema(description = "Requested page size", example = "20") int pageSize,
        @Schema(description = "Products matching every filter", example = "42") long totalElements,
        @Schema(description = "Number of pages of pageSize", example = "3") int totalPages,
        ProductSearchFacetsDto facets) {}
