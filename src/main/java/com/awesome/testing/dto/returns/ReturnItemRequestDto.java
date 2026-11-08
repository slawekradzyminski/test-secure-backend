package com.awesome.testing.dto.returns;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product and quantity to return")
public class ReturnItemRequestDto {
    @NotNull(message = "Product ID is required")
    @Schema(description = "Product ID from the order", example = "3")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Schema(minimum = "1", description = "Units to return", example = "1")
    private Integer quantity;
}
