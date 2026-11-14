package com.awesome.testing.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.util.UUID;

/**
 * One event of the internal feed. Always exactly these ten fields in this order (nulls
 * included); {@code occurredAt} is ISO-8601 UTC with millisecond precision and {@code data}
 * is the stored JSON object written verbatim, so the same event always has the same bytes.
 * {@code requestId} and {@code traceId} (3.17.0, additive) identify the request that wrote the
 * event so consumers can continue its trace; {@code spanId} (additive) is the writing span, the
 * parent of a consumer's {@code process <type>} span. All three are null for events written
 * without a request.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"sequence", "id", "type", "occurredAt", "orderId", "username", "data", "requestId", "traceId",
        "spanId"})
public record FeedEventDto(
        long sequence,
        UUID id,
        String type,
        String occurredAt,
        Long orderId,
        String username,
        @JsonRawValue String data,
        String requestId,
        String traceId,
        String spanId) {
}
