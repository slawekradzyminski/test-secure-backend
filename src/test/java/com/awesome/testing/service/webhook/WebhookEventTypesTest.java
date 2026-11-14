package com.awesome.testing.service.webhook;

import com.awesome.testing.dto.webhook.WebhookEventTypeDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookEventTypesTest {

    @Test
    void listsEveryCommerceWireNameAndPing() {
        assertThat(WebhookEventTypes.all()).extracting(WebhookEventTypeDto::name).containsExactly(
                "order.created", "order.paid", "order.cancelled", "shipment.shipped", "shipment.delivered",
                "refund.booked", "payment.failed", "order.reservation-expired", "webhook.ping");
        assertThat(WebhookEventTypes.all()).allSatisfy(type -> assertThat(type.description()).isNotBlank());
        assertThat(WebhookEventTypes.wireNames()).hasSize(8).doesNotContain("card-refund.requested");
    }

    @Test
    void internalServiceEventsCannotBeSubscribed() {
        assertThatThrownBy(() -> WebhookEventTypes.normalize(List.of("card-refund.requested")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unknown event type 'card-refund.requested'");
    }

    @Test
    void normalizesToDistinctTrimmedNamesInRequestOrder() {
        assertThat(WebhookEventTypes.normalize(List.of(" order.paid", "order.created", "order.paid ")))
                .containsExactly("order.paid", "order.created");
    }

    @Test
    void rejectsUnknownNames() {
        assertThatThrownBy(() -> WebhookEventTypes.normalize(List.of("order.created", "ORDER_CREATED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unknown event type 'ORDER_CREATED'. Allowed: order.created, order.paid");
        assertThatThrownBy(() -> WebhookEventTypes.normalize(java.util.Arrays.asList("order.created", null)))
                .hasMessageStartingWith("Unknown event type ''");
    }
}
