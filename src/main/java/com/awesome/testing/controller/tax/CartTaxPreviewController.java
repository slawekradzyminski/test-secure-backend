package com.awesome.testing.controller.tax;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.tax.TaxPreviewDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.tax.CartTaxPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart management endpoints")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class CartTaxPreviewController {

    private final CartTaxPreviewService cartTaxPreviewService;

    @GetMapping("/api/v1/cart/tax-preview")
    @Operation(summary = "Preview cart VAT", description = "Returns the VAT contained in the cart total (after any coupon) for a shipping country given as a code or name. The cart itself is unchanged.")
    @ApiResponse(responseCode = "200", description = "VAT preview returned")
    @ApiResponse(responseCode = "400", description = "Country missing",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "Country not recognised or no rate configured",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public TaxPreviewDto preview(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Parameter(description = "Shipping country code or name", example = "PL")
            @RequestParam(required = false) String country) {
        return cartTaxPreviewService.preview(principal.getUsername(), country);
    }
}
