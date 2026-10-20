package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.entity.TrafficLogEntity;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@Component
@RequiredArgsConstructor
public class TrafficLogMapper {
    private final ObjectMapper objectMapper;

    public TrafficLogEntryDto toDto(TrafficLogEntity entity) {
        return TrafficLogEntryDto.builder()
                .correlationId(entity.getCorrelationId())
                .timestamp(entity.getTimestamp())
                .clientSessionId(entity.getClientSessionId())
                .method(entity.getMethod())
                .path(entity.getPath())
                .queryString(entity.getQueryString())
                .status(entity.getStatus())
                .durationMs(entity.getDurationMs())
                .requestHeaders(parseJsonObject(entity.getRequestHeaders()))
                .requestContentType(entity.getRequestContentType())
                .requestBody(parseBody(entity.getRequestBody(), entity.getRequestContentType()))
                .requestBodyTruncated(entity.isRequestBodyTruncated())
                .requestBodyOriginalLength(entity.getRequestBodyOriginalLength())
                .requestBodyStoredLength(entity.getRequestBodyStoredLength())
                .responseHeaders(parseJsonObject(entity.getResponseHeaders()))
                .responseContentType(entity.getResponseContentType())
                .responseBody(parseBody(entity.getResponseBody(), entity.getResponseContentType()))
                .responseBodyTruncated(entity.isResponseBodyTruncated())
                .responseBodyOriginalLength(entity.getResponseBodyOriginalLength())
                .responseBodyStoredLength(entity.getResponseBodyStoredLength())
                .build();
    }

    private JsonNode parseJsonObject(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private JsonNode parseBody(String rawBody, String contentType) {
        if (rawBody == null) {
            return JsonNodeFactory.instance.stringNode("");
        }
        if (isJsonContentType(contentType)) {
            try {
                return objectMapper.readTree(rawBody);
            } catch (Exception ignored) {
                // Fall through to preserve the stored preview as text when truncation or invalid JSON prevents parsing.
            }
        }
        return JsonNodeFactory.instance.stringNode(rawBody);
    }

    private boolean isJsonContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return mediaType.isCompatibleWith(MediaType.APPLICATION_JSON) || mediaType.getSubtype().endsWith("+json");
        } catch (Exception ex) {
            return false;
        }
    }
}
