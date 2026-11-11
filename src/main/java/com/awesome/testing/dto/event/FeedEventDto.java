package com.awesome.testing.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.util.UUID;

/**
 * One event of the internal feed. Always exactly these seven fields in this order (nulls
 * included); {@code occurredAt} is ISO-8601 UTC with millisecond precision and {@code data}
 * is the stored JSON object written verbatim, so the same event always has the same bytes.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"sequence", "id", "type", "occurredAt", "orderId", "username", "data"})
public record FeedEventDto(
        long sequence,
        UUID id,
        String type,
        String occurredAt,
        Long orderId,
        String username,
        @JsonRawValue String data) {
}
