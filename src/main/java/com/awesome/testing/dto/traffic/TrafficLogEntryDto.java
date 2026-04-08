package com.awesome.testing.dto.traffic;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Schema(description = "Serialized request headers")
    private String requestHeaders;

    @Schema(description = "Sanitized request body")
    private String requestBody;

    @Schema(description = "Serialized response headers")
    private String responseHeaders;

    @Schema(description = "Sanitized response body")
    private String responseBody;
}
