package com.awesome.testing.controller.webhook;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.webhook.WebhookSubscriptionDto;
import com.awesome.testing.service.webhook.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequestMapping("/api/v1/admin/webhooks")
@Tag(name = "Webhooks", description = "Signed HTTP callbacks for your order events, with a delivery log and retries")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class AdminWebhookController {

    private final WebhookService webhookService;

    @GetMapping
    @Operation(summary = "List all webhook subscriptions",
            description = "Administrators list every user's subscriptions by id, optionally for one username. Secrets "
                    + "are never included. Negative page is clamped to zero; size is clamped to 1–100.")
    @ApiResponse(responseCode = "200", description = "Subscriptions returned")
    @ApiResponse(responseCode = "400", description = "Invalid parameter",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public PageDto<WebhookSubscriptionDto> list(
            @Parameter(description = "Only this user's subscriptions") @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return webhookService.adminList(username, page, size);
    }
}
