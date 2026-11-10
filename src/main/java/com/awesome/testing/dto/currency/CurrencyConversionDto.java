package com.awesome.testing.dto.currency;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Amount converted between two currencies through their USD rates")
public class CurrencyConversionDto {

    @Schema(description = "Amount in the source currency", example = "100.00")
    private BigDecimal amount;

    @Schema(description = "Source currency code", example = "USD")
    private String from;

    @Schema(description = "Target currency code", example = "EUR")
    private String to;

    @Schema(description = "Converted amount, rounded half-up to 2 decimals from the exact rate", example = "92.59")
    private BigDecimal converted;

    @Schema(description = "Units of the target currency per unit of the source currency, rounded half-up to 6 decimals",
            example = "0.925926")
    private BigDecimal rate;
}
