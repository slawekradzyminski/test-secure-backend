package com.awesome.testing.controller.loyalty;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.loyalty.ApplyLoyaltyPointsDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.CartService;
import com.awesome.testing.service.tender.CartTenderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cart/loyalty-points")
@Tag(name = "Cart", description = "Shopping cart management endpoints")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class CartLoyaltyController {

    private final CartTenderService cartTenderService;
    private final CartService cartService;

    @PutMapping
    @Operation(summary = "Apply loyalty points to cart", description = "Reserves loyalty points for checkout in whole multiples of 100 (100 points = 1.00), replacing any previous amount. "
            + "Points pay after the coupon discount and the gift card; the cart shows pointsRedeemed, capped at what the remaining total needs, pointsAmount and amountDue. "
            + "Checkout checks the balance again.")
    @ApiResponse(responseCode = "200", description = "Points applied; the cart shows the points amount")
    @ApiResponse(responseCode = "400", description = "Empty cart, or points below 100 or not a multiple of 100",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "409", description = "Insufficient loyalty points",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public CartDto apply(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody ApplyLoyaltyPointsDto request) {
        cartTenderService.applyPoints(principal.getUsername(), request.getPoints());
        return cartService.getCart(principal.getUsername());
    }

    @DeleteMapping
    @Operation(summary = "Remove loyalty points from cart", description = "Stops using loyalty points for this cart. Succeeds even when no points are applied.")
    @ApiResponse(responseCode = "200", description = "Points removed; the updated cart is returned")
    public CartDto remove(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        cartTenderService.removePoints(principal.getUsername());
        return cartService.getCart(principal.getUsername());
    }
}
