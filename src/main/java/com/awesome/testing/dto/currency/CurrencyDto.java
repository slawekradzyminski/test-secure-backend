package com.awesome.testing.dto.currency;

import com.awesome.testing.entity.currency.CurrencyRateEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Display currency with a fixed demo exchange rate. Prices are stored in USD.")
public class CurrencyDto {

    @Schema(description = "ISO 4217 code", example = "EUR")
    private String code;

    @Schema(description = "Currency name", example = "Euro")
    private String name;

    @Schema(description = "Display symbol", example = "€")
    private String symbol;

    @Schema(description = "Value of one unit in USD", example = "1.080000")
    private BigDecimal rateToUsd;

    @Schema(type = "string", format = "local-date-time", description = "Last change (server-local)")
    private LocalDateTime updatedAt;

    public static CurrencyDto from(CurrencyRateEntity entity) {
        return CurrencyDto.builder()
                .code(entity.getCode())
                .name(entity.getName())
                .symbol(entity.getSymbol())
                .rateToUsd(entity.getRateToUsd())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
