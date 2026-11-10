package com.awesome.testing.dto.tax;

import com.awesome.testing.entity.tax.VatRateEntity;
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
@Schema(description = "Standard VAT rate of a country. Prices are VAT-inclusive; a rate of 0 means VAT is not applicable.")
public class VatRateDto {

    @Schema(description = "ISO 3166-1 alpha-2 country code", example = "PL")
    private String countryCode;

    @Schema(description = "Country name", example = "Poland")
    private String countryName;

    @Schema(description = "Rate in percent", example = "23.00")
    private BigDecimal rate;

    @Schema(type = "string", format = "local-date-time", description = "Last change (server-local)")
    private LocalDateTime updatedAt;

    public static VatRateDto from(VatRateEntity entity) {
        return VatRateDto.builder()
                .countryCode(entity.getCountryCode())
                .countryName(entity.getCountryName())
                .rate(entity.getRate())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
