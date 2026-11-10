package com.awesome.testing.dto.tax;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "VAT rate definition. Changes apply to orders created afterwards; existing orders keep their rate.")
public class VatRateRequestDto {

    @NotBlank(message = "Country name is required")
    @Size(max = 64, message = "Country name cannot exceed 64 characters")
    @Schema(minLength = 1, maxLength = 64, pattern = "\\S", description = "Country name", example = "Poland")
    private String countryName;

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.00", message = "Rate must be between 0 and 50")
    @DecimalMax(value = "50.00", message = "Rate must be between 0 and 50")
    @Digits(integer = 2, fraction = 2, message = "Rate must have at most 2 decimals")
    @Schema(minimum = "0", maximum = "50", multipleOf = 0.01, description = "Rate in percent; 0 means VAT is not applicable",
            example = "23.00")
    private BigDecimal rate;
}
