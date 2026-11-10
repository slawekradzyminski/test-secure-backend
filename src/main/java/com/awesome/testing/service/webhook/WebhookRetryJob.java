package com.awesome.testing.service.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Sends webhook deliveries whose retry is due, and deliveries that were never sent because
 * the backend stopped or the dispatch queue was full. Backoff and the attempt limit live in
 * {@link WebhookDeliveryLog}; {@code webhooks.retry.enabled=false} turns the schedule off
 * (tests call {@link #retryDueDeliveries()} directly).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookRetryJob {

    private final WebhookDeliveryLog deliveryLog;
    private final WebhookDispatcher dispatcher;
    private final WebhookProperties properties;

    @Scheduled(fixedDelayString = "${webhooks.retry.interval:PT30S}",
            initialDelayString = "${webhooks.retry.initial-delay:PT30S}")
    public void scheduledRetry() {
        if (properties.getRetry().isEnabled()) {
            retryDueDeliveries();
        }
    }

    /** Attempts every due delivery once, in the calling thread; returns how many were attempted. */
    public int retryDueDeliveries() {
        List<UUID> due = deliveryLog.dueIds(properties.getRetry().getBatchSize());
        if (!due.isEmpty()) {
            log.info("Retrying {} webhook deliveries", due.size());
        }
        int attempted = 0;
        for (UUID id : due) {
            if (dispatcher.dispatch(id).isPresent()) {
                attempted++;
            }
        }
        return attempted;
    }
}
