package com.awesome.testing.controller.tax;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.tax.VatRateDto;
import com.awesome.testing.service.tax.VatRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vat-rates")
@Tag(name = "VAT", description = "VAT rates. Prices are VAT-inclusive; orders show the VAT their total contains.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class VatRateController {

    private final VatRateService vatRateService;

    @GetMapping
    @Operation(summary = "List VAT rates", description = "Lists the standard VAT rate of every configured country, ordered by country code. A rate of 0 means VAT is not applicable.")
    @ApiResponse(responseCode = "200", description = "VAT rates returned")
    public List<VatRateDto> list() {
        return vatRateService.list();
    }

    @GetMapping("/{country}")
    @Operation(summary = "Get VAT rate", description = "Returns the VAT rate for a country given as an ISO alpha-2 or alpha-3 code or an English name (also common variants such as UK, USA, Polska or Deutschland), ignoring case.")
    @ApiResponse(responseCode = "200", description = "VAT rate returned")
    @ApiResponse(responseCode = "404", description = "Country not recognised or no rate configured",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public VatRateDto get(@Parameter(description = "Country code or name", example = "PL") @PathVariable String country) {
        return vatRateService.get(country);
    }
}
