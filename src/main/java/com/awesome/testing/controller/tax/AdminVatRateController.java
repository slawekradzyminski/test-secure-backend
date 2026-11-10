package com.awesome.testing.controller.tax;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.tax.VatRateDto;
import com.awesome.testing.dto.tax.VatRateRequestDto;
import com.awesome.testing.service.tax.VatRateService;
import com.awesome.testing.service.tax.VatRateUpsert;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequestMapping("/api/v1/admin/vat-rates")
@Tag(name = "VAT", description = "VAT rates. Prices are VAT-inclusive; orders show the VAT their total contains.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class AdminVatRateController {

    private final VatRateService vatRateService;

    @PutMapping("/{countryCode}")
    @Operation(summary = "Create or update VAT rate", description = "Sets the VAT rate (0-50, at most 2 decimals) and name of a country. The code is an ISO 3166-1 alpha-2 code, case-insensitive; UK is stored as GB. Existing orders keep the rate they were created with.")
    @ApiResponse(responseCode = "200", description = "VAT rate updated")
    @ApiResponse(responseCode = "201", description = "VAT rate created")
    @ApiResponse(responseCode = "400", description = "Invalid country code or rate definition",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    public ResponseEntity<VatRateDto> upsert(
            @Parameter(description = "ISO 3166-1 alpha-2 country code", example = "PL") @PathVariable String countryCode,
            @Valid @RequestBody VatRateRequestDto request) {
        VatRateUpsert result = vatRateService.upsert(countryCode, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.rate());
    }
}
