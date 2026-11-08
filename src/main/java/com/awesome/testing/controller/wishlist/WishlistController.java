package com.awesome.testing.controller.wishlist;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.wishlist.WishlistItemDto;
import com.awesome.testing.dto.wishlist.WishlistUpdateDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.wishlist.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wishlist")
@Tag(name = "Wishlist", description = "Saved products and back-in-stock notifications")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    @Operation(summary = "Get wishlist", description = "Returns the caller's wishlist, most recently added first, with current price and stock.")
    @ApiResponse(responseCode = "200", description = "Wishlist returned")
    public List<WishlistItemDto> list(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        return wishlistService.list(principal.getUsername());
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Add or update wishlist product", description = "Adds a product to the wishlist (201) or updates its notification setting (200). The body is optional; notifyWhenAvailable defaults to false. A wishlist holds at most 50 products.")
    @ApiResponse(responseCode = "200", description = "Wishlist entry updated")
    @ApiResponse(responseCode = "201", description = "Wishlist entry created")
    @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Wishlist is full",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<WishlistItemDto> upsert(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long productId,
            @RequestBody(required = false) WishlistUpdateDto request) {
        boolean notify = request != null && Boolean.TRUE.equals(request.getNotifyWhenAvailable());
        WishlistService.Upsert result = wishlistService.upsert(principal.getUsername(), productId, notify);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.item());
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Remove wishlist product", description = "Removes a product from the caller's wishlist.")
    @ApiResponse(responseCode = "204", description = "Wishlist entry removed", content = @Content)
    @ApiResponse(responseCode = "404", description = "Product is not on the wishlist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<Void> remove(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long productId) {
        wishlistService.remove(principal.getUsername(), productId);
        return ResponseEntity.noContent().build();
    }
}
