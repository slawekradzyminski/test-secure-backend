package com.awesome.testing.controller.giftcard;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.giftcard.GiftCardBalanceDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.giftcard.GiftCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/gift-cards")
@Tag(name = "Gift cards", description = "Gift card balance check")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class GiftCardController {

    private final GiftCardService giftCardService;
    private final AuthRateLimitGuard rateLimitGuard;

    @GetMapping("/{code}")
    @Operation(summary = "Check gift card balance", description = "Returns the balance, effective status and expiry of a gift card. "
            + "Any signed-in user holding the code may check it; the recipient and note are never shown. "
            + "Lookups share a per-user rate limit with applying gift cards to the cart.")
    @ApiResponse(responseCode = "200", description = "Gift card balance returned")
    @ApiResponse(responseCode = "400", description = "Malformed code",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "Gift card not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public GiftCardBalanceDto lookup(
            HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Parameter(description = "Case-insensitive gift card code", example = "GC-7KQ2-M9XD-4HPA") @PathVariable String code) {
        rateLimitGuard.checkGiftCard(request, principal.getUsername());
        return giftCardService.lookup(code);
    }
}
