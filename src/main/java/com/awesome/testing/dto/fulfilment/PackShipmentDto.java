package com.awesome.testing.dto.fulfilment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
@Schema(description = "Packing result")
public class PackShipmentDto {
    @NotNull(message = "Package weight is required")
    @DecimalMin(value = "0.01", message = "Package weight must be between 0.01 and 30.00 kg")
    @DecimalMax(value = "30.00", message = "Package weight must be between 0.01 and 30.00 kg")
    @Digits(integer = 2, fraction = 2, message = "Package weight must have at most 2 decimals")
    @Schema(minimum = "0.01", maximum = "30.00", multipleOf = 0.01, description = "Package weight in kilograms", example = "1.25")
    private BigDecimal weightKg;
}
