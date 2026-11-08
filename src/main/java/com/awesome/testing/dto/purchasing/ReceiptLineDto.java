package com.awesome.testing.dto.purchasing;

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
@Schema(description = "Units received for one product")
public class ReceiptLineDto {
    @NotNull(message = "Product ID is required")
    @Schema(description = "Product ID", example = "3")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Schema(minimum = "1", description = "Units received", example = "20")
    private Integer quantity;
}
