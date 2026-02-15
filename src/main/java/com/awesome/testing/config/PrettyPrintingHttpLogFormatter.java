package com.awesome.testing.config;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.zalando.logbook.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class PrettyPrintingHttpLogFormatter implements HttpLogFormatter {
    private final ObjectMapper mapper;

    @Override
    @SuppressWarnings("all")
    public String format(Precorrelation precorrelation, HttpRequest request) throws IOException {
        Map<String, Object> content = toJson(request, precorrelation);
        return formatWithPrettyBody(content);
    }

    @Override
    @SuppressWarnings("all")
    public String format(Correlation correlation, HttpResponse response) throws IOException {
        Map<String, Object> content = toJson(response, correlation);
        return formatWithPrettyBody(content);
    }

    private Map<String, Object> toJson(HttpRequest request, Precorrelation precorrelation) throws IOException {
        String body = request.getBodyAsString();
        return Map.of(
                "origin", "remote",
                "type", "request",
                "correlation", precorrelation.getId(),
                "protocol", request.getProtocolVersion(),
                "method", request.getMethod(),
                "uri", request.getRequestUri(),
                "headers", request.getHeaders(),
                "body", body
        );
    }

    private Map<String, Object> toJson(HttpResponse response, Correlation correlation) throws IOException {
        String body = response.getBodyAsString();
        return Map.of(
                "origin", "local",
                "type", "response",
                "correlation", correlation.getId(),
                "duration", correlation.getDuration().toMillis(),
                "protocol", response.getProtocolVersion(),
                "status", response.getStatus(),
                "headers", response.getHeaders(),
                "body", body
        );
    }

    private String formatWithPrettyBody(Map<String, Object> content) throws IOException {
        if (content.containsKey("body") && content.get("body") instanceof String) {
            try {
                Object body = mapper.readValue((String) content.get("body"), Object.class);
                Map<String, Object> mutableContent = new HashMap<>(content);
                mutableContent.put("body", body);
                content = mutableContent;
            } catch (JacksonException ignored) {
                // If body is not JSON, leave it as is
            }
        }
        return mapper.writeValueAsString(content);
    }
} 
