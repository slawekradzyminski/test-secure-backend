package com.awesome.testing.controller.webhook;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.webhook.WebhookInboxDto;
import com.awesome.testing.dto.webhook.WebhookInboxRequestDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.webhook.WebhookInboxService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/webhook-inboxes")
@Tag(name = "Webhook inboxes", description = "Built-in webhook receivers, so no external server is needed")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class WebhookInboxController {

    private static final String NOT_FOUND = "Inbox not found (or it belongs to another user)";

    private final WebhookInboxService inboxService;

    @PostMapping
    @Operation(summary = "Create webhook inbox",
            description = "Creates a receiver addressed as inbox://<token>. Use that URL in a subscription; deliveries "
                    + "to it are signed and logged like HTTP deliveries but stored here. At most 3 inboxes per user.")
    @ApiResponse(responseCode = "201", description = "Inbox created")
    @ApiResponse(responseCode = "409", description = "Inbox limit reached",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<WebhookInboxDto> create(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inboxService.create(principal.getUsername()));
    }

    @GetMapping
    @Operation(summary = "List my webhook inboxes", description = "Returns the caller's inboxes, oldest first.")
    @ApiResponse(responseCode = "200", description = "Inboxes returned")
    public List<WebhookInboxDto> list(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        return inboxService.list(principal.getUsername());
    }

    @GetMapping("/{token}/requests")
    @Operation(summary = "List received webhook requests",
            description = "Requests delivered to the inbox with their headers and body, newest first; the last 50 are kept.")
    @ApiResponse(responseCode = "200", description = "Requests returned")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public List<WebhookInboxRequestDto> requests(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
                                                 @PathVariable String token) {
        return inboxService.requests(principal.getUsername(), token);
    }

    @DeleteMapping("/{token}")
    @Operation(summary = "Delete webhook inbox",
            description = "Deletes the inbox and its requests. Subscriptions that target it fail until they are updated.")
    @ApiResponse(responseCode = "204", description = "Inbox deleted", content = @Content)
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
                                       @PathVariable String token) {
        inboxService.delete(principal.getUsername(), token);
        return ResponseEntity.noContent().build();
    }
}
