package com.awesome.testing.dto.webhook;

import com.awesome.testing.entity.webhook.WebhookInboxEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Built-in webhook receiver")
public record WebhookInboxDto(
        @Schema(description = "Inbox token", example = "Q2xhdWRlIGlzIGEgZ29vZCBib3QgOik") String token,
        @Schema(description = "Webhook URL to subscribe with", example = "inbox://Q2xhdWRlIGlzIGEgZ29vZCBib3QgOik")
        String url,
        @Schema(description = "Creation time (UTC)", example = "2026-11-10T20:40:33Z") Instant createdAt) {

    public static final String SCHEME_PREFIX = "inbox://";

    public static WebhookInboxDto from(WebhookInboxEntity entity) {
        return new WebhookInboxDto(entity.getToken(), SCHEME_PREFIX + entity.getToken(), entity.getCreatedAt());
    }
}
