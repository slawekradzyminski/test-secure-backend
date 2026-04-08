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

    @Schema(description = "Client-provided session identifier")
    private String clientSessionId;

    @Schema(description = "HTTP method")
    private String method;

    @Schema(description = "Request path")
    private String path;

    @Schema(description = "Request query string")
    private String queryString;

    @Schema(description = "Response status code")
    private int status;

    @Schema(description = "Request duration in milliseconds")
    private long durationMs;

    @Schema(description = "Request headers")
    private JsonNode requestHeaders;

    @Schema(description = "Request content type")
    private String requestContentType;

    @Schema(description = "Sanitized request body")
    private JsonNode requestBody;

    @Schema(description = "Whether the request body was truncated")
    private boolean requestBodyTruncated;

    @Schema(description = "Sanitized request body length before truncation")
    private int requestBodyOriginalLength;

    @Schema(description = "Stored request body length")
    private int requestBodyStoredLength;

    @Schema(description = "Response headers")
    private JsonNode responseHeaders;

    @Schema(description = "Response content type")
    private String responseContentType;

    @Schema(description = "Sanitized response body")
    private JsonNode responseBody;

    @Schema(description = "Whether the response body was truncated")
    private boolean responseBodyTruncated;

    @Schema(description = "Sanitized response body length before truncation")
    private int responseBodyOriginalLength;

    @Schema(description = "Stored response body length")
    private int responseBodyStoredLength;
}
