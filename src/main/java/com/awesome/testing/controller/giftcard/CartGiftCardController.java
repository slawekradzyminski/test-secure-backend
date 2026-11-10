package com.awesome.testing.controller.giftcard;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.giftcard.ApplyGiftCardDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.CartService;
import com.awesome.testing.service.tender.CartTenderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/cart/gift-card")
@Tag(name = "Cart", description = "Shopping cart management endpoints")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class CartGiftCardController {

    private final CartTenderService cartTenderService;
    private final CartService cartService;
    private final AuthRateLimitGuard rateLimitGuard;

    @PutMapping
    @Operation(summary = "Apply gift card to cart", description = "Validates the gift card and attaches it to the cart, replacing any previous one. "
            + "The cart then shows giftCardCode, giftCardAmount and amountDue; the card pays after the coupon discount and before loyalty points. "
            + "Checkout validates the card again.")
    @ApiResponse(responseCode = "200", description = "Gift card applied; the cart shows the gift card amount")
    @ApiResponse(responseCode = "400", description = "Empty cart, malformed code, disabled or expired gift card",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "404", description = "Gift card not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Gift card has no remaining balance",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public CartDto apply(
            HttpServletRequest httpRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody ApplyGiftCardDto request) {
        rateLimitGuard.checkGiftCard(httpRequest, principal.getUsername());
        cartTenderService.applyGiftCard(principal.getUsername(), request.getCode());
        return cartService.getCart(principal.getUsername());
    }

    @DeleteMapping
    @Operation(summary = "Remove gift card from cart", description = "Detaches the gift card from the cart. Succeeds even when no gift card is applied.")
    @ApiResponse(responseCode = "200", description = "Gift card removed; the updated cart is returned")
    public CartDto remove(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        cartTenderService.removeGiftCard(principal.getUsername());
        return cartService.getCart(principal.getUsername());
    }
}
