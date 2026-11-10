package com.awesome.testing.controller;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.product.search.ProductSearchResultDto;
import com.awesome.testing.service.search.ProductSearchCriteria;
import com.awesome.testing.service.search.ProductSearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    @GetMapping("/search")
    @Operation(summary = "Search products",
            description = "Searches the catalog with an optional case-insensitive text query over name, category and "
                    + "description (substring match; % and _ are literal characters), category, price, rating and "
                    + "stock filters, sorting and paging. Ratings are averages of approved reviews. Facets cover the "
                    + "products matching every filter except category, so the category facet lists the alternatives "
                    + "to the selected categories; priceRange and inStockCount use the same set. relevance (the "
                    + "default when q is given) ranks name matches above category matches above description "
                    + "matches; without q the default and the relevance fallback is newest. Every invalid parameter "
                    + "is reported in one 400 response keyed by parameter name, including values of the wrong type.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search page with facets"),
        @ApiResponse(responseCode = "400", description = "Invalid search parameters",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ValidationErrorsDto.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    })
    @SuppressWarnings("java:S107")
    public ProductSearchResultDto search(
            @Parameter(description = "Text to find in name, category or description; trimmed, case-insensitive, "
                    + "at most 100 characters", example = "laptop")
            @RequestParam(required = false) String q,
            @Parameter(description = "Category filter, case-insensitive; repeat the parameter or separate values "
                    + "with commas (at most 20)",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "Electronics")))
            @RequestParam(required = false) List<String> category,
            @Parameter(description = "Lowest price, inclusive, zero or greater", example = "10.00",
                    schema = @Schema(type = "number", minimum = "0"))
            @RequestParam(required = false) String minPrice,
            @Parameter(description = "Highest price, inclusive, zero or greater and not below minPrice",
                    example = "500.00", schema = @Schema(type = "number", minimum = "0"))
            @RequestParam(required = false) String maxPrice,
            @Parameter(description = "Lowest average approved-review rating, 1-5; products without approved "
                    + "reviews are excluded", example = "4",
                    schema = @Schema(type = "number", minimum = "1", maximum = "5"))
            @RequestParam(required = false) String minRating,
            @Parameter(description = "true returns only products with stock above zero; false or omitted applies "
                    + "no stock filter", schema = @Schema(type = "boolean"))
            @RequestParam(required = false) String inStock,
            @Parameter(description = "relevance (default with q), newest (default without q), price_asc, "
                    + "price_desc, name_asc or rating_desc",
                    schema = @Schema(type = "string", allowableValues = {
                        "relevance", "newest", "price_asc", "price_desc", "name_asc", "rating_desc"}))
            @RequestParam(required = false) String sort,
            @Parameter(description = "Zero-based page index", schema = @Schema(type = "integer", minimum = "0",
                    defaultValue = "0"))
            @RequestParam(required = false) String page,
            @Parameter(description = "Page size, 1-100", schema = @Schema(type = "integer", minimum = "1",
                    maximum = "100", defaultValue = "20"))
            @RequestParam(required = false) String size) {
        return productSearchService.search(
                ProductSearchCriteria.parse(q, category, minPrice, maxPrice, minRating, inStock, sort, page, size));
    }
}
