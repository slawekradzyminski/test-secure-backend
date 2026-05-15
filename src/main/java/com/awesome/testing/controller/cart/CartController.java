package com.awesome.testing.controller.cart;

import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart management endpoints")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get current user's cart",
            description = "Returns the authenticated user's cart, including item count and calculated total price.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved cart")
    public ResponseEntity<CartDto> getCart(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        return ResponseEntity.ok(cartService.getCart(principal.getUsername()));
    }

    @DeleteMapping
    @Operation(summary = "Clear cart",
            description = "Removes all items from the authenticated user's cart.")
    @ApiResponse(responseCode = "204", description = "Cart cleared successfully")
    public ResponseEntity<Void> clearCart(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        cartService.clearCart(principal.getUsername());
        return ResponseEntity.noContent().build();
    }
}
