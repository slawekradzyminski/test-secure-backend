package com.awesome.testing.controller.loyalty;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.loyalty.LoyaltyAccountDto;
import com.awesome.testing.dto.loyalty.LoyaltyTransactionDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.loyalty.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/loyalty")
@Tag(name = "Loyalty", description = "Loyalty points of the signed-in user")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @GetMapping
    @Operation(summary = "Get loyalty account", description = "Returns the caller's points balance, lifetime points, tier and the value of the balance. "
            + "Points are earned when an order becomes PAID (1 point per full 1.00 of its total, excluding the part paid with points) and redeemed at 100 points = 1.00. "
            + "Users who never earned points get a zero balance.")
    @ApiResponse(responseCode = "200", description = "Loyalty account returned")
    public LoyaltyAccountDto account(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        return loyaltyService.account(principal.getUsername());
    }

    @GetMapping("/transactions")
    @Operation(summary = "List loyalty transactions", description = "Lists the caller's points ledger newest first: EARNED, REDEEMED, RESTORED and REVOKED with signed points. "
            + "Negative page is clamped to zero; size is clamped to 1-100.")
    @ApiResponse(responseCode = "200", description = "Loyalty transactions returned")
    public PageDto<LoyaltyTransactionDto> transactions(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "20") int size) {
        return PageDto.from(loyaltyService.transactions(principal.getUsername(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id").descending())));
    }
}
