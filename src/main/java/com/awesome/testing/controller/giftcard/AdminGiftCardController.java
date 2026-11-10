package com.awesome.testing.controller.giftcard;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.giftcard.GiftCardCreateDto;
import com.awesome.testing.dto.giftcard.GiftCardDto;
import com.awesome.testing.dto.giftcard.GiftCardStatus;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.giftcard.GiftCardService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequestMapping("/api/v1/admin/gift-cards")
@Tag(name = "Gift cards", description = "Administrator gift card management")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class AdminGiftCardController {

    private final GiftCardService giftCardService;

    @PostMapping
    @Operation(summary = "Issue gift card", description = "Issues a gift card of 1.00-1000.00 with a generated GC-XXXX-XXXX-XXXX code. "
            + "With recipientUsername the user receives a GIFT_CARD_ISSUED email with the code.")
    @ApiResponse(responseCode = "201", description = "Gift card issued")
    @ApiResponse(responseCode = "400", description = "Amount out of range, expiresAt not in the future, or field too long",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "404", description = "Recipient user not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<GiftCardDto> issue(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody GiftCardCreateDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(giftCardService.issue(request, principal.getUsername()));
    }

    @GetMapping
    @Operation(summary = "List gift cards", description = "Lists gift cards newest first, optionally only those with the given effective status. "
            + "Negative page is clamped to zero; size is clamped to 1-100.")
    @ApiResponse(responseCode = "200", description = "Gift cards returned")
    @ApiResponse(responseCode = "400", description = "Unknown status",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public PageDto<GiftCardDto> list(
            @Parameter(description = "Effective status filter") @RequestParam(required = false) GiftCardStatus status,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "20") int size) {
        return PageDto.from(giftCardService.list(status,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id").descending())));
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get gift card", description = "Returns one gift card with its recipient, note and ledger (ISSUED, REDEEMED, RESTORED, DISABLED), oldest first. The code is case-insensitive.")
    @ApiResponse(responseCode = "200", description = "Gift card returned")
    @ApiResponse(responseCode = "400", description = "Malformed code",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "Gift card not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public GiftCardDto get(@Parameter(description = "Gift card code", example = "GC-7KQ2-M9XD-4HPA") @PathVariable String code) {
        return giftCardService.get(code);
    }

    @PostMapping("/{code}/disable")
    @Operation(summary = "Disable gift card", description = "Disables a gift card permanently. The balance is kept but can no longer be applied or spent; "
            + "checkout with a cart that still holds the card fails with 409.")
    @ApiResponse(responseCode = "200", description = "Gift card disabled")
    @ApiResponse(responseCode = "400", description = "Malformed code",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "Gift card not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Gift card is already disabled",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public GiftCardDto disable(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Parameter(description = "Gift card code", example = "GC-7KQ2-M9XD-4HPA") @PathVariable String code) {
        return giftCardService.disable(code, principal.getUsername());
    }
}
