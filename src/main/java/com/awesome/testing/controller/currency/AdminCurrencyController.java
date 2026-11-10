package com.awesome.testing.controller.currency;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.currency.CurrencyDto;
import com.awesome.testing.dto.currency.CurrencyRequestDto;
import com.awesome.testing.service.currency.CurrencyService;
import com.awesome.testing.service.currency.CurrencyUpsert;
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
@RequestMapping("/api/v1/admin/currencies")
@Tag(name = "Currencies", description = "Display currencies with fixed demo exchange rates; prices and orders stay in USD")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class AdminCurrencyController {

    private final CurrencyService currencyService;

    @PutMapping("/{code}")
    @Operation(summary = "Create or update currency", description = "Sets the name, symbol and USD rate (greater than 0, at most 6 decimals) of a currency. The code is a 3-letter ISO 4217 code, case-insensitive. USD is the base currency and its rate must stay 1.")
    @ApiResponse(responseCode = "200", description = "Currency updated")
    @ApiResponse(responseCode = "201", description = "Currency created")
    @ApiResponse(responseCode = "400", description = "Invalid currency code or definition",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    public ResponseEntity<CurrencyDto> upsert(
            @Parameter(description = "ISO 4217 currency code", example = "EUR") @PathVariable String code,
            @Valid @RequestBody CurrencyRequestDto request) {
        CurrencyUpsert result = currencyService.upsert(code, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.currency());
    }
}
