package com.awesome.testing.dto.traffic;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Schema(description = "Persisted HTTP traffic log entry")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrafficLogEntryDto {

    @Schema(description = "Traffic correlation identifier")
    private String correlationId;

    @Schema(description = "Request timestamp")
    private Instant timestamp;

    @Schema(types = {"string", "null"}, description = "Client-provided session identifier")
    private String clientSessionId;

    @Schema(description = "HTTP method")
    private String method;

    @Schema(description = "Request path")
    private String path;

    @Schema(types = {"string", "null"}, description = "Request query string")
    private String queryString;

    @Schema(description = "Response status code")
    private int status;

    @Schema(description = "Request duration in milliseconds")
    private long durationMs;

    @Schema(implementation = Object.class, type = "object", additionalProperties = Schema.AdditionalPropertiesValue.TRUE, description = "Request headers as a JSON object")
    private JsonNode requestHeaders;

    @Schema(types = {"string", "null"}, description = "Request content type")
    private String requestContentType;

    @Schema(implementation = Object.class, types = {"object", "array", "string", "number", "boolean", "null"}, description = "Sanitized request body: parsed JSON of any type, or a text preview for non-JSON/truncated content")
    private JsonNode requestBody;

    @Schema(description = "Whether the request body was truncated")
    private boolean requestBodyTruncated;

    @Schema(description = "Sanitized request body length before truncation")
    private int requestBodyOriginalLength;

    @Schema(description = "Stored request body length")
    private int requestBodyStoredLength;

    @Schema(implementation = Object.class, type = "object", additionalProperties = Schema.AdditionalPropertiesValue.TRUE, description = "Response headers as a JSON object")
    private JsonNode responseHeaders;

    @Schema(types = {"string", "null"}, description = "Response content type")
    private String responseContentType;

    @Schema(implementation = Object.class, types = {"object", "array", "string", "number", "boolean", "null"}, description = "Sanitized response body: parsed JSON of any type, or a text preview for non-JSON/truncated content")
    private JsonNode responseBody;

    @Schema(description = "Whether the response body was truncated")
    private boolean responseBodyTruncated;

    @Schema(description = "Sanitized response body length before truncation")
    private int responseBodyOriginalLength;

    @Schema(description = "Stored response body length")
    private int responseBodyStoredLength;
}
