package com.awesome.testing.service.webhook;

import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebhookEventListenerTest {

    private final WebhookDeliveryLog deliveryLog = mock(WebhookDeliveryLog.class);
    private final WebhookDispatcher dispatcher = mock(WebhookDispatcher.class);
    private final WebhookProperties properties = new WebhookProperties();
    private final WebhookEventListener listener = new WebhookEventListener(deliveryLog, dispatcher, properties);

    @Test
    void publicEventsAreFannedOut() {
        CommerceEvent event = event(CommerceEventType.REFUND_BOOKED);
        List<UUID> deliveries = List.of(UUID.randomUUID());
        when(deliveryLog.recordEvent(event)).thenReturn(deliveries);

        listener.onCommerceEvent(event);

        verify(dispatcher).dispatchAll(deliveries);
    }

    @Test
    void cardRefundRequestsNeverReachUserWebhooks() {
        listener.onCommerceEvent(event(CommerceEventType.CARD_REFUND_REQUESTED));

        verifyNoInteractions(deliveryLog, dispatcher);
    }

    @Test
    void notificationRequestsNeverReachUserWebhooks() {
        listener.onCommerceEvent(event(CommerceEventType.NOTIFICATION_REQUESTED));

        verifyNoInteractions(deliveryLog, dispatcher);
    }

    @Test
    void nothingIsRecordedOrSentWhileWebhooksAreDisabled() {
        properties.setEnabled(false);

        listener.onCommerceEvent(event(CommerceEventType.ORDER_CREATED));

        verifyNoInteractions(deliveryLog, dispatcher);
    }

    private static CommerceEvent event(CommerceEventType type) {
        return new CommerceEvent(UUID.randomUUID(), type, "buyer", 1L, Instant.now(), Map.of("orderId", 1L));
    }
}
