package com.awesome.testing.dto.purchasing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Purchase order line")
public class PurchaseOrderLineRequestDto {
    @NotNull(message = "Product ID is required")
    @Schema(description = "Product ID", example = "3")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be between 1 and 100000")
    @Max(value = 100000, message = "Quantity must be between 1 and 100000")
    @Schema(minimum = "1", maximum = "100000", description = "Units ordered", example = "50")
    private Integer quantity;

    @NotNull(message = "Unit cost is required")
    @DecimalMin(value = "0.01", message = "Unit cost must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Unit cost must have at most 8 digits and 2 decimals")
    @Schema(multipleOf = 0.01, description = "Purchase price per unit", example = "640.00")
    private BigDecimal unitCost;
}
