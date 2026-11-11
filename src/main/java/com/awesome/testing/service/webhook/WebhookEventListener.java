package com.awesome.testing.service.webhook;

import com.awesome.testing.event.CommerceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

/**
 * Fans committed commerce events out to the owner's webhook subscriptions: the delivery
 * rows are written in their own transaction first, so a crash before sending leaves them
 * PENDING for the retry job. Errors never reach the business request that committed.
 * Internal service events (not {@link com.awesome.testing.event.CommerceEventType#offeredToWebhooks()})
 * are ignored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventListener {

    private final WebhookDeliveryLog deliveryLog;
    private final WebhookDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommerceEvent(CommerceEvent event) {
        if (!event.type().offeredToWebhooks()) {
            return;
        }
        try {
            List<UUID> deliveries = deliveryLog.recordEvent(event);
            dispatcher.dispatchAll(deliveries);
        } catch (RuntimeException e) {
            log.error("Could not record webhook deliveries for event {} ({})", event.id(), event.type(), e);
        }
    }
}
