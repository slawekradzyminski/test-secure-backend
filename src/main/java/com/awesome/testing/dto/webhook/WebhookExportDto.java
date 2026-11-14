package com.awesome.testing.dto.webhook;

import java.time.Instant;
import java.util.List;

/**
 * Every webhook subscription (with its plain signing secret) and inbox, for the one-time import
 * of the notification-service ({@code GET /internal/v1/webhooks/export}). Deliveries and inbox
 * requests are not exported. Internal only: never part of the public OpenAPI document.
 */
public record WebhookExportDto(List<Subscription> subscriptions, List<Inbox> inboxes) {

    public record Subscription(Long id, String username, String url, List<String> events, String secret,
                               boolean active, String description, Instant createdAt, Instant updatedAt,
                               int consecutiveFailures) {
    }

    public record Inbox(String token, String username, Instant createdAt) {
    }
}
