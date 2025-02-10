package com.awesome.testing.dto.cart;

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
public class CartItemDto {

    @Schema(description = "Product id", example = "13")
    @NotNull
    private Long productId;

    @Schema(description = "Product quantity", example = "1")
    @NotNull
    @Min(1)
    private Integer quantity;

}