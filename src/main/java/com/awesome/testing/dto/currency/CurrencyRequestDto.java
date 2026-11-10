package com.awesome.testing.dto.currency;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Currency definition. USD is the base currency and its rate must stay 1.")
public class CurrencyRequestDto {

    @NotBlank(message = "Currency name is required")
    @Size(max = 64, message = "Currency name cannot exceed 64 characters")
    @Schema(minLength = 1, maxLength = 64, pattern = "\\S", description = "Currency name", example = "Euro")
    private String name;

    @NotBlank(message = "Currency symbol is required")
    @Size(max = 8, message = "Currency symbol cannot exceed 8 characters")
    @Schema(minLength = 1, maxLength = 8, pattern = "\\S", description = "Display symbol", example = "€")
    private String symbol;

    @NotNull(message = "Rate to USD is required")
    @DecimalMin(value = "0", inclusive = false, message = "Rate to USD must be greater than 0")
    @Digits(integer = 6, fraction = 6, message = "Rate to USD must have at most 6 digits and 6 decimals")
    @Schema(minimum = "0.000001", maximum = "999999.999999", multipleOf = 0.000001,
            description = "Value of one unit in USD", example = "1.08")
    private BigDecimal rateToUsd;
}
