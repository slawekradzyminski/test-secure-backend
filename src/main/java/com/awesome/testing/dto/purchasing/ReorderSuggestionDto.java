package com.awesome.testing.dto.purchasing;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product that should be reordered")
public class ReorderSuggestionDto {
    @Schema(description = "Product ID", example = "3")
    private Long productId;
    @Schema(description = "Product name", example = "iPhone 13 Pro")
    private String productName;
    @Schema(description = "Category", example = "Electronics")
    private String category;
    @Schema(description = "Stock on hand", example = "2")
    private int stockQuantity;
    @Schema(description = "Units outstanding on SUBMITTED or PARTIALLY_RECEIVED purchase orders", example = "0")
    private int incomingQuantity;
    @Schema(description = "4 × threshold − stock − incoming", example = "18")
    private int suggestedQuantity;
}
