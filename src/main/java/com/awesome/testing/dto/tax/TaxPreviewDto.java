package com.awesome.testing.dto.tax;

import com.awesome.testing.service.tax.VatBreakdown;
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
@Schema(description = "VAT contained in the cart total (after any coupon) for a shipping country")
public class TaxPreviewDto {

    @Schema(description = "Resolved ISO 3166-1 alpha-2 country code", example = "PL")
    private String country;

    @Schema(description = "VAT rate in percent", example = "23.00")
    private BigDecimal rate;

    @Schema(description = "VAT contained in the total", example = "18.70")
    private BigDecimal taxAmount;

    @Schema(description = "Total without VAT", example = "81.29")
    private BigDecimal netAmount;

    @Schema(description = "VAT-inclusive cart total after any coupon discount", example = "99.99")
    private BigDecimal totalAmount;

    public static TaxPreviewDto from(String country, VatBreakdown breakdown) {
        return TaxPreviewDto.builder()
                .country(country)
                .rate(breakdown.rate())
                .taxAmount(breakdown.taxAmount())
                .netAmount(breakdown.netAmount())
                .totalAmount(breakdown.totalAmount())
                .build();
    }
}
