package com.awesome.testing.dto.webhook;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An event type a webhook can subscribe to")
public record WebhookEventTypeDto(
        @Schema(description = "Wire name used in events[] and X-Webhook-Event", example = "order.created") String name,
        @Schema(description = "When it is sent", example = "An order was placed") String description) {
}
