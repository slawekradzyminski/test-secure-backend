package com.awesome.testing.dto.webhook;

import com.awesome.testing.entity.webhook.WebhookSubscriptionEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Webhook subscription")
public class WebhookSubscriptionDto {

    @Schema(description = "Subscription id", example = "1")
    private Long id;

    @Schema(description = "Owner", example = "client")
    private String username;

    @Schema(description = "Target URL", example = "https://example.com/webhooks/shop")
    private String url;

    @Schema(types = {"string", "null"}, description = "Free-text label", example = "Order sync")
    private String description;

    @Schema(description = "Subscribed event types", example = "[\"order.created\"]")
    private List<String> events;

    @Schema(description = "Whether events are delivered", example = "true")
    private boolean active;

    @Schema(description = "Failed attempts since the last successful delivery; 20 disables the subscription",
            example = "0")
    private int consecutiveFailures;

    @Schema(types = {"string", "null"}, description = "Why the subscription was disabled automatically",
            example = "Disabled after 20 consecutive failed deliveries")
    private String disabledReason;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Signing secret. Returned only by create and rotate-secret; store it, it is never shown again.",
            example = "whsec_q9nZ2l0aHViLmNvbS93ZWJob29rcy1zZWNyZXQtZXhhbXBsZQ")
    private String secret;

    @Schema(description = "Creation time (UTC)", example = "2026-11-10T20:40:33Z")
    private Instant createdAt;

    @Schema(description = "Last change (UTC)", example = "2026-11-10T20:40:33Z")
    private Instant updatedAt;

    public static WebhookSubscriptionDto from(WebhookSubscriptionEntity entity) {
        return from(entity, null);
    }

    public static WebhookSubscriptionDto from(WebhookSubscriptionEntity entity, String revealedSecret) {
        return WebhookSubscriptionDto.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .url(entity.getUrl())
                .description(entity.getDescription())
                .events(entity.eventList())
                .active(entity.isActive())
                .consecutiveFailures(entity.getConsecutiveFailures())
                .disabledReason(entity.getDisabledReason())
                .secret(revealedSecret)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
