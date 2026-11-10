package com.awesome.testing.service.webhook;

import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookPayloadsTest {

    private static final UUID ID = UUID.fromString("6a1f0f57-0f7c-4bb4-8a43-2f9d8f7e8c11");

    @Test
    void writesAFixedEnvelopeWithSortedData() {
        // given
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalAmount", new BigDecimal("1E+2").setScale(2));
        data.put("status", "PENDING");
        data.put("orderId", 42L);
        CommerceEvent event = new CommerceEvent(ID, CommerceEventType.ORDER_SHIPPED, "alice", 42L,
                Instant.parse("2026-11-10T20:40:33.123456Z"), data);

        // when
        String body = WebhookPayloads.forEvent(event);

        // then
        assertThat(body).isEqualTo("{\"id\":\"6a1f0f57-0f7c-4bb4-8a43-2f9d8f7e8c11\",\"type\":\"shipment.shipped\","
                + "\"createdAt\":\"2026-11-10T20:40:33.123Z\",\"data\":{\"orderId\":42,\"status\":\"PENDING\","
                + "\"totalAmount\":100.00}}");
    }

    @Test
    void pingsNameTheSubscription() {
        assertThat(WebhookPayloads.ping(ID, Instant.parse("2026-11-10T20:40:33Z"), 5L))
                .isEqualTo("{\"id\":\"6a1f0f57-0f7c-4bb4-8a43-2f9d8f7e8c11\",\"type\":\"webhook.ping\","
                        + "\"createdAt\":\"2026-11-10T20:40:33Z\",\"data\":{\"message\":\"Webhook test from Awesome Testing\","
                        + "\"subscriptionId\":5}}");
    }
}
