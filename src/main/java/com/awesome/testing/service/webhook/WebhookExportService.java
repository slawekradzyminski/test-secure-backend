package com.awesome.testing.service.webhook;

import com.awesome.testing.dto.webhook.WebhookExportDto;
import com.awesome.testing.entity.webhook.WebhookInboxEntity;
import com.awesome.testing.entity.webhook.WebhookSubscriptionEntity;
import com.awesome.testing.repository.webhook.WebhookInboxRepository;
import com.awesome.testing.repository.webhook.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the webhook export read once by the notification-service when it takes over webhooks:
 * subscriptions and inboxes ordered by id, ids kept, signing secrets decrypted so the importer
 * can keep signing with them.
 */
@Service
@RequiredArgsConstructor
public class WebhookExportService {

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookInboxRepository inboxes;
    private final WebhookSecrets secrets;

    @Transactional(readOnly = true)
    public WebhookExportDto export() {
        return new WebhookExportDto(
                subscriptions.findAll(Sort.by("id")).stream().map(this::subscription).toList(),
                inboxes.findAll(Sort.by("id")).stream().map(WebhookExportService::inbox).toList());
    }

    private WebhookExportDto.Subscription subscription(WebhookSubscriptionEntity entity) {
        return new WebhookExportDto.Subscription(entity.getId(), entity.getUsername(), entity.getUrl(),
                entity.eventList(), secrets.reveal(entity.getSecret()), entity.isActive(), entity.getDescription(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getConsecutiveFailures());
    }

    private static WebhookExportDto.Inbox inbox(WebhookInboxEntity entity) {
        return new WebhookExportDto.Inbox(entity.getToken(), entity.getUsername(), entity.getCreatedAt());
    }
}
