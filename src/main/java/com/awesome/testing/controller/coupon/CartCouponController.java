package com.awesome.testing.controller.coupon;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.coupon.ApplyCouponDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.CartService;
import com.awesome.testing.service.coupon.CouponService;
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
@RequestMapping("/api/v1/cart/coupon")
@Tag(name = "Cart", description = "Shopping cart management endpoints")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class CartCouponController {

    private final CouponService couponService;
    private final CartService cartService;

    @PutMapping
    @Operation(summary = "Apply coupon to cart", description = "Validates the coupon against the current cart and attaches it, replacing any previous coupon. Checkout validates it again.")
    @ApiResponse(responseCode = "200", description = "Coupon applied; the cart shows the discount")
    @ApiResponse(responseCode = "400", description = "Empty cart, inactive, not yet valid or expired coupon, or subtotal below the minimum",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "404", description = "Coupon not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Global or per-user redemption limit reached",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public CartDto apply(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody ApplyCouponDto request) {
        couponService.applyToCart(principal.getUsername(), request.getCode());
        return cartService.getCart(principal.getUsername());
    }

    @DeleteMapping
    @Operation(summary = "Remove coupon from cart", description = "Detaches the coupon from the cart. Succeeds even when no coupon is applied.")
    @ApiResponse(responseCode = "200", description = "Coupon removed; the updated cart is returned")
    public CartDto remove(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        couponService.removeFromCart(principal.getUsername());
        return cartService.getCart(principal.getUsername());
    }
}
