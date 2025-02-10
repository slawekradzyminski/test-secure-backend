package com.awesome.testing.controller.cart;

import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart management endpoints")
public class CartItemsController {

    private final CartService cartService;

    @PostMapping("/items")
    @Operation(summary = "Add item to cart", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)
    })
    public ResponseEntity<CartDto> addToCart(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody CartItemDto cartItemDto) {
        return ResponseEntity.ok(cartService.addToCart(principal.toString(), cartItemDto));
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Update item quantity", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item quantity updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Cart item not found", content = @Content)
    })
    public ResponseEntity<CartDto> updateCartItem(
            @AuthenticationPrincipal Object principal,
            @PathVariable Long productId,
            @Valid @RequestBody CartItemDto cartItemDto) {
        return ResponseEntity.ok(cartService.updateCartItem(principal.toString(), productId, cartItemDto));
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove item from cart", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item removed successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Cart item not found", content = @Content)
    })
    public ResponseEntity<CartDto> removeFromCart(
            @AuthenticationPrincipal Object principal,
            @PathVariable Long productId) {
        return ResponseEntity.ok(cartService.removeFromCart(principal.toString(), productId));
    }

}