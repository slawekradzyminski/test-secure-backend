package com.awesome.testing.controller.review;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.review.MyReviewDto;
import com.awesome.testing.dto.review.ReviewDto;
import com.awesome.testing.dto.review.ReviewRequestDto;
import com.awesome.testing.dto.review.ReviewSummaryDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.review.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/{productId}/reviews")
@Tag(name = "Reviews", description = "Product reviews and ratings")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "404", description = "Product or review not found",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class ProductReviewController {

    private final ReviewService reviewService;

    @GetMapping
    @Operation(summary = "List approved reviews", description = "Lists approved reviews for a product, newest first. Negative page is clamped to zero; size is clamped to 1-50.")
    @ApiResponse(responseCode = "200", description = "Reviews returned")
    public PageDto<ReviewDto> list(
            @PathVariable Long productId,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-50") @RequestParam(defaultValue = "10") int size) {
        return PageDto.from(reviewService.approved(productId, PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get rating summary", description = "Returns the average rating, count, and 1-5 distribution over approved reviews only.")
    @ApiResponse(responseCode = "200", description = "Summary returned")
    public ReviewSummaryDto summary(@PathVariable Long productId) {
        return reviewService.summary(productId);
    }

    @GetMapping("/mine")
    @Operation(summary = "Get my review and eligibility", description = "Returns whether the caller may review the product and the caller's review in any moderation status.")
    @ApiResponse(responseCode = "200", description = "Eligibility returned")
    public MyReviewDto mine(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long productId) {
        return reviewService.mine(principal.getUsername(), productId);
    }

    @PostMapping
    @Operation(summary = "Create review", description = "Creates a PENDING review. Only users with a DELIVERED order containing the product may review it, once per product.")
    @ApiResponse(responseCode = "201", description = "Review created and awaiting moderation")
    @ApiResponse(responseCode = "400", description = "Invalid review",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    @ApiResponse(responseCode = "403", description = "The caller has not received this product",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "The caller already reviewed this product",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<ReviewDto> create(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long productId,
            @Valid @RequestBody ReviewRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(principal.getUsername(), productId, request));
    }

    @PutMapping("/mine")
    @Operation(summary = "Edit my review", description = "Replaces the caller's review content. The review returns to PENDING and its moderation note is cleared.")
    @ApiResponse(responseCode = "200", description = "Review updated and awaiting moderation")
    @ApiResponse(responseCode = "400", description = "Invalid review",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    public ReviewDto update(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long productId,
            @Valid @RequestBody ReviewRequestDto request) {
        return reviewService.updateMine(principal.getUsername(), productId, request);
    }

    @DeleteMapping("/mine")
    @Operation(summary = "Delete my review", description = "Deletes the caller's review for the product.")
    @ApiResponse(responseCode = "204", description = "Review deleted", content = @Content)
    public ResponseEntity<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long productId) {
        reviewService.deleteMine(principal.getUsername(), productId);
        return ResponseEntity.noContent().build();
    }
}
