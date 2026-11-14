package com.awesome.testing.controller.webhook;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.webhook.WebhookCreateDto;
import com.awesome.testing.dto.webhook.WebhookDeliveryDto;
import com.awesome.testing.dto.webhook.WebhookDeliveryStatus;
import com.awesome.testing.dto.webhook.WebhookEventTypeDto;
import com.awesome.testing.dto.webhook.WebhookSubscriptionDto;
import com.awesome.testing.dto.webhook.WebhookUpdateDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.webhook.WebhookEventTypes;
import com.awesome.testing.service.webhook.WebhookRateLimitGuard;
import com.awesome.testing.service.webhook.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Signed HTTP callbacks for your order events, with a delivery log and retries")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class WebhookController {

    private static final String FORBIDDEN = "The subscription belongs to another user";
    private static final String NOT_FOUND = "Subscription not found";

    private final WebhookService webhookService;
    private final WebhookRateLimitGuard rateLimitGuard;

    @GetMapping("/event-types")
    @Operation(summary = "List webhook event types",
            description = "Event names a subscription can list in events[]; each is also sent as X-Webhook-Event.")
    @ApiResponse(responseCode = "200", description = "Event types returned")
    public List<WebhookEventTypeDto> eventTypes() {
        return WebhookEventTypes.all();
    }

    @GetMapping
    @Operation(summary = "List my webhook subscriptions",
            description = "Returns the caller's subscriptions, oldest first. Secrets are never included.")
    @ApiResponse(responseCode = "200", description = "Subscriptions returned")
    public List<WebhookSubscriptionDto> list(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        return webhookService.list(principal.getUsername());
    }

    @PostMapping
    @Operation(summary = "Create webhook subscription",
            description = "Subscribes a public http(s) URL on port 80 or 443 (or inbox://<token> of one of your inboxes) "
                    + "to your order events. URLs resolving to loopback, private, link-local or other internal "
                    + "addresses are rejected. The response contains the signing secret, which is shown only once. "
                    + "At most 5 subscriptions per user.")
    @ApiResponse(responseCode = "201", description = "Subscription created; secret included once")
    @ApiResponse(responseCode = "400", description = "Invalid body, unknown event type or rejected URL",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "409", description = "Subscription limit reached",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<WebhookSubscriptionDto> create(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody WebhookCreateDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(webhookService.create(principal.getUsername(), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get webhook subscription", description = "Returns one of the caller's subscriptions.")
    @ApiResponse(responseCode = "200", description = "Subscription returned")
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public WebhookSubscriptionDto get(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
                                      @PathVariable Long id) {
        return webhookService.get(principal.getUsername(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update webhook subscription",
            description = "Replaces url, events and description; active=false pauses deliveries and active=true "
                    + "re-enables an (auto-)disabled subscription and resets its failure counter. The URL is checked "
                    + "again. The secret is unchanged.")
    @ApiResponse(responseCode = "200", description = "Subscription updated")
    @ApiResponse(responseCode = "400", description = "Invalid body, unknown event type or rejected URL",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public WebhookSubscriptionDto update(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
                                         @PathVariable Long id,
                                         @Valid @RequestBody WebhookUpdateDto request) {
        return webhookService.update(principal.getUsername(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete webhook subscription", description = "Deletes the subscription and its delivery log.")
    @ApiResponse(responseCode = "204", description = "Subscription deleted", content = @Content)
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
                                       @PathVariable Long id) {
        webhookService.delete(principal.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rotate-secret")
    @Operation(summary = "Rotate webhook secret",
            description = "Replaces the signing secret and returns the new one once. Later deliveries, including "
                    + "retries of earlier events, are signed with it.")
    @ApiResponse(responseCode = "200", description = "Secret rotated; new secret included once")
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public WebhookSubscriptionDto rotateSecret(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
                                               @PathVariable Long id) {
        return webhookService.rotateSecret(principal.getUsername(), id);
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Send test webhook",
            description = "Sends a webhook.ping event now (waiting up to 5 seconds for the receiver) and returns the "
                    + "recorded delivery. A failed ping is retried like any other delivery.")
    @ApiResponse(responseCode = "201", description = "Ping sent; the delivery shows the outcome")
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Subscription is disabled, or webhook delivery is disabled on this backend",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<WebhookDeliveryDto> test(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
                                                   @PathVariable Long id) {
        rateLimitGuard.checkTest(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(webhookService.test(principal.getUsername(), id));
    }

    @GetMapping("/{id}/deliveries")
    @Operation(summary = "List webhook deliveries",
            description = "Delivery log of a subscription, newest first, optionally filtered by status. Negative page "
                    + "is clamped to zero; size is clamped to 1–100.")
    @ApiResponse(responseCode = "200", description = "Deliveries returned")
    @ApiResponse(responseCode = "400", description = "Invalid parameter",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public PageDto<WebhookDeliveryDto> deliveries(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id,
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return webhookService.deliveries(principal.getUsername(), id, status, page, size);
    }

    @GetMapping("/{id}/deliveries/{deliveryId}")
    @Operation(summary = "Get webhook delivery",
            description = "Returns one delivery with its payload, last status code, error and response snippet.")
    @ApiResponse(responseCode = "200", description = "Delivery returned")
    @ApiResponse(responseCode = "400", description = "Invalid delivery id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "Subscription or delivery not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public WebhookDeliveryDto delivery(@Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
                                       @PathVariable Long id,
                                       @PathVariable UUID deliveryId) {
        return webhookService.delivery(principal.getUsername(), id, deliveryId);
    }

    @PostMapping("/{id}/deliveries/{deliveryId}/redeliver")
    @Operation(summary = "Redeliver webhook",
            description = "Sends the event of an earlier delivery again, now, as a new delivery with the same event id "
                    + "and body and a fresh signature. The original delivery is unchanged.")
    @ApiResponse(responseCode = "201", description = "Redelivery sent; the new delivery shows the outcome")
    @ApiResponse(responseCode = "400", description = "Invalid delivery id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "Subscription or delivery not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Subscription is disabled, or webhook delivery is disabled on this backend",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<WebhookDeliveryDto> redeliver(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id,
            @PathVariable UUID deliveryId) {
        rateLimitGuard.checkRedeliver(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(webhookService.redeliver(principal.getUsername(), id, deliveryId));
    }
}
