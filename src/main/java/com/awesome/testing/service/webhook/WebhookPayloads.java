package com.awesome.testing.service.webhook;

import com.awesome.testing.event.CommerceEvent;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Builds webhook bodies {@code {"id","type","createdAt","data"}} with a fixed field order
 * and {@code data} keys sorted, so the same event always produces the same bytes. A
 * dedicated mapper keeps profile-specific Jackson settings (such as indentation) out of
 * signed payloads.
 */
public final class WebhookPayloads {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private WebhookPayloads() {
    }

    public static String forEvent(CommerceEvent event) {
        return body(event.id(), event.type().wireName(), event.occurredAt(), event.data());
    }

    public static String ping(UUID eventId, Instant createdAt, Long subscriptionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subscriptionId", subscriptionId);
        data.put("message", "Webhook test from Awesome Testing");
        return body(eventId, WebhookEventTypes.PING, createdAt, data);
    }

    static String body(UUID id, String type, Instant createdAt, Map<String, ?> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.toString());
        body.put("type", type);
        body.put("createdAt", DateTimeFormatter.ISO_INSTANT.format(createdAt.truncatedTo(ChronoUnit.MILLIS)));
        body.put("data", new TreeMap<>(data == null ? Map.of() : data));
        return MAPPER.writeValueAsString(body);
    }
}
