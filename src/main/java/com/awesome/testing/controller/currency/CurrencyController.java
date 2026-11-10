package com.awesome.testing.controller.currency;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.currency.CurrencyConversionDto;
import com.awesome.testing.dto.currency.CurrencyDto;
import com.awesome.testing.service.currency.CurrencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/currencies")
@Tag(name = "Currencies", description = "Display currencies with fixed demo exchange rates; prices and orders stay in USD")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping
    @Operation(summary = "List currencies", description = "Lists the display currencies ordered by code. rateToUsd is the USD value of one unit; USD is the base currency.")
    @ApiResponse(responseCode = "200", description = "Currencies returned")
    public List<CurrencyDto> list() {
        return currencyService.list();
    }

    @GetMapping("/convert")
    @Operation(summary = "Convert amount", description = "Converts an amount (0 or more, at most 2 decimals) between two currencies through their USD rates. converted is rounded half-up to 2 decimals from the exact rate; rate is rounded half-up to 6 decimals. Currency codes are case-insensitive.")
    @ApiResponse(responseCode = "200", description = "Amount converted")
    @ApiResponse(responseCode = "400", description = "Missing or invalid amount or currency code",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "Currency not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public CurrencyConversionDto convert(
            @Parameter(description = "Amount in the source currency", example = "100.00")
            @RequestParam(required = false) String amount,
            @Parameter(description = "Source currency code; defaults to USD", example = "USD")
            @RequestParam(required = false) String from,
            @Parameter(description = "Target currency code", example = "EUR")
            @RequestParam(required = false) String to) {
        return currencyService.convert(amount, from, to);
    }
}
