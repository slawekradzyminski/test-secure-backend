package com.awesome.testing.dto.webhook;

import com.awesome.testing.entity.webhook.WebhookDeliveryEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One event sent to a webhook subscription and the outcome of its last attempt")
public class WebhookDeliveryDto {

    @Schema(description = "Delivery id, sent as X-Webhook-Id", example = "0b8f4f0e-3f43-4b8e-9d55-4a3f1c1f6a10")
    private UUID id;

    @Schema(description = "Subscription id", example = "1")
    private Long subscriptionId;

    @Schema(description = "Event id (the payload's id); shared by redeliveries of the same event",
            example = "6a1f0f57-0f7c-4bb4-8a43-2f9d8f7e8c11")
    private UUID eventId;

    @Schema(description = "Event type, sent as X-Webhook-Event", example = "order.created")
    private String eventType;

    @Schema(description = "Delivery status", example = "DELIVERED")
    private WebhookDeliveryStatus status;

    @Schema(description = "Attempts made so far", example = "1")
    private int attempts;

    @Schema(types = {"string", "null"}, format = "date-time", description = "When the next retry is due (PENDING only)")
    private Instant nextAttemptAt;

    @Schema(types = {"integer", "null"}, description = "HTTP status of the last attempt, if a response arrived",
            example = "200")
    private Integer lastStatusCode;

    @Schema(types = {"string", "null"}, description = "Why the last attempt failed", example = "HTTP 500")
    private String lastError;

    @Schema(types = {"string", "null"}, description = "First 500 characters of the last response body",
            example = "ok")
    private String responseSnippet;

    @Schema(types = {"integer", "null"}, description = "Duration of the last attempt in milliseconds", example = "42")
    private Long durationMs;

    @Schema(description = "The exact JSON body that was signed and sent",
            example = "{\"id\":\"6a1f0f57-0f7c-4bb4-8a43-2f9d8f7e8c11\",\"type\":\"order.created\","
                    + "\"createdAt\":\"2026-11-10T20:40:33.120Z\",\"data\":{\"orderId\":1,\"status\":\"PENDING\","
                    + "\"totalAmount\":40.00}}")
    private String payload;

    @Schema(description = "Creation time (UTC)", example = "2026-11-10T20:40:33Z")
    private Instant createdAt;

    @Schema(types = {"string", "null"}, format = "date-time", description = "When a 2xx response was received")
    private Instant deliveredAt;

    public static WebhookDeliveryDto from(WebhookDeliveryEntity entity) {
        return WebhookDeliveryDto.builder()
                .id(entity.getId())
                .subscriptionId(entity.getSubscriptionId())
                .eventId(entity.getEventId())
                .eventType(entity.getEventType())
                .status(entity.getStatus())
                .attempts(entity.getAttempts())
                .nextAttemptAt(entity.getNextAttemptAt())
                .lastStatusCode(entity.getLastStatusCode())
                .lastError(entity.getLastError())
                .responseSnippet(entity.getResponseSnippet())
                .durationMs(entity.getDurationMs())
                .payload(entity.getPayload())
                .createdAt(entity.getCreatedAt())
                .deliveredAt(entity.getDeliveredAt())
                .build();
    }
}
