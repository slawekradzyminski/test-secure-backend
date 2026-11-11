package com.awesome.testing.event;

import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.TreeMap;

/**
 * Serializes an event's {@code data} for the feed: keys sorted, decimals plain, no
 * indentation, so the same data always produces the same bytes. A dedicated mapper keeps
 * profile-specific Jackson settings out of stored payloads.
 */
public final class DomainEventPayloads {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private DomainEventPayloads() {
    }

    public static String data(Map<String, ?> data) {
        return MAPPER.writeValueAsString(new TreeMap<>(data == null ? Map.of() : data));
    }
}
