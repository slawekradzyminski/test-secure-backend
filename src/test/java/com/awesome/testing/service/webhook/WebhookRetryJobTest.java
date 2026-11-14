package com.awesome.testing.service.webhook;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebhookRetryJobTest {

    private final WebhookDeliveryLog deliveryLog = mock(WebhookDeliveryLog.class);
    private final WebhookDispatcher dispatcher = mock(WebhookDispatcher.class);
    private final WebhookProperties properties = new WebhookProperties();
    private final WebhookRetryJob job = new WebhookRetryJob(deliveryLog, dispatcher, properties);

    @Test
    void theScheduleRetriesDueDeliveriesWhileWebhooksAreEnabled() {
        UUID due = UUID.randomUUID();
        when(deliveryLog.dueIds(anyInt())).thenReturn(List.of(due));
        when(dispatcher.dispatch(due)).thenReturn(Optional.empty());

        job.scheduledRetry();

        verify(dispatcher).dispatch(due);
    }

    @Test
    void theScheduleDoesNothingWhileWebhooksAreDisabled() {
        properties.setEnabled(false);

        job.scheduledRetry();

        verifyNoInteractions(deliveryLog, dispatcher);
    }

    @Test
    void theScheduleDoesNothingWhileRetriesAreDisabled() {
        properties.getRetry().setEnabled(false);

        job.scheduledRetry();

        verifyNoInteractions(deliveryLog, dispatcher);
    }
}
