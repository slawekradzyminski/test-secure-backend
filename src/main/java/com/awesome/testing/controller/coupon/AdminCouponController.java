package com.awesome.testing.controller.coupon;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.coupon.CouponDto;
import com.awesome.testing.dto.coupon.CouponRequestDto;
import com.awesome.testing.dto.order.PageDto;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequestMapping("/api/v1/admin/coupons")
@Tag(name = "Coupons", description = "Administrator coupon management")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class AdminCouponController {

    private final CouponService couponService;

    @GetMapping
    @Operation(summary = "List coupons", description = "Lists coupons newest first with their effective status. Negative page is clamped to zero; size is clamped to 1-100.")
    @ApiResponse(responseCode = "200", description = "Coupons returned")
    public PageDto<CouponDto> list(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "20") int size) {
        return PageDto.from(couponService.list(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id").descending())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get coupon", description = "Returns one coupon with its redemption count and effective status.")
    @ApiResponse(responseCode = "200", description = "Coupon returned")
    @ApiResponse(responseCode = "404", description = "Coupon not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public CouponDto get(@PathVariable Long id) {
        return couponService.get(id);
    }

    @PostMapping
    @Operation(summary = "Create coupon", description = "Creates a coupon. Codes are case-insensitive and stored upper-case. PERCENTAGE values above 100 and validity windows that end before they start are rejected.")
    @ApiResponse(responseCode = "201", description = "Coupon created")
    @ApiResponse(responseCode = "400", description = "Invalid coupon definition",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "409", description = "Coupon code already exists",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<CouponDto> create(@Valid @RequestBody CouponRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(couponService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update coupon", description = "Replaces a coupon definition. The redemption count is preserved.")
    @ApiResponse(responseCode = "200", description = "Coupon updated")
    @ApiResponse(responseCode = "400", description = "Invalid coupon definition",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "404", description = "Coupon not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Coupon code already exists",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public CouponDto update(@PathVariable Long id, @Valid @RequestBody CouponRequestDto request) {
        return couponService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete coupon", description = "Deletes a coupon that was never redeemed and removes it from carts. Redeemed coupons return 409; deactivate them instead.")
    @ApiResponse(responseCode = "204", description = "Coupon deleted", content = @Content)
    @ApiResponse(responseCode = "404", description = "Coupon not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Coupon has redemptions",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        couponService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
