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
public class UpdateCartItemDto {

    @Schema(description = "Product quantity", example = "1")
    @NotNull
    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer quantity;

}
