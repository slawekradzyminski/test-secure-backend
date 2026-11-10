package com.awesome.testing.dto.webhook;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "A webhook request received by an inbox")
public record WebhookInboxRequestDto(
        @Schema(description = "Request id", example = "7") Long id,
        @Schema(description = "Receive time (UTC)", example = "2026-11-10T20:40:33Z") Instant receivedAt,
        @Schema(description = "Request headers",
                example = "{\"X-Webhook-Event\":\"webhook.ping\",\"X-Webhook-Signature\":\"t=1794346833,v1=5f2b...\"}")
        Map<String, String> headers,
        @Schema(description = "Raw request body (at most 16 KB)", example = "{\"id\":\"...\",\"type\":\"webhook.ping\"}")
        String body) {
}
