package com.awesome.testing.dto.fulfilment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product to pick")
public class PickListItemDto {
    @Schema(description = "Product ID", example = "3")
    private Long productId;
    @Schema(description = "Product name", example = "iPhone 13 Pro")
    private String productName;
    @Schema(description = "Category, used as the warehouse aisle", example = "Electronics")
    private String category;
    @Schema(description = "Units to pick", example = "2")
    private int quantity;
}
