package com.awesome.testing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
        name = "traffic_log",
        indexes = {
                @Index(name = "idx_traffic_log_timestamp", columnList = "timestamp"),
                @Index(name = "idx_traffic_log_client_session_id", columnList = "client_session_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TrafficLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "correlation_id", nullable = false, length = 64, unique = true)
    private String correlationId;

    @Column(name = "client_session_id", length = 128)
    private String clientSessionId;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(nullable = false, length = 16)
    private String method;

    @Column(nullable = false, length = 1024)
    private String path;

    @Column(name = "query_string", length = 2000)
    private String queryString;

    @Column(nullable = false)
    private int status;

    @Column(name = "request_content_type", length = 255)
    private String requestContentType;

    @Column(name = "request_body_truncated", nullable = false)
    private boolean requestBodyTruncated;

    @Column(name = "request_body_original_length", nullable = false)
    private int requestBodyOriginalLength;

    @Column(name = "request_body_stored_length", nullable = false)
    private int requestBodyStoredLength;

    @Lob
    @Column(name = "request_headers")
    private String requestHeaders;

    @Lob
    @Column(name = "request_body")
    private String requestBody;

    @Lob
    @Column(name = "response_headers")
    private String responseHeaders;

    @Column(name = "response_content_type", length = 255)
    private String responseContentType;

    @Column(name = "response_body_truncated", nullable = false)
    private boolean responseBodyTruncated;

    @Column(name = "response_body_original_length", nullable = false)
    private int responseBodyOriginalLength;

    @Column(name = "response_body_stored_length", nullable = false)
    private int responseBodyStoredLength;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    private TrafficLogEntity(Long id,
                             String correlationId,
                             String clientSessionId,
                             Instant timestamp,
                             long durationMs,
                             String method,
                             String path,
                             String queryString,
                             int status,
                             String requestContentType,
                             boolean requestBodyTruncated,
                             int requestBodyOriginalLength,
                             int requestBodyStoredLength,
                             String requestHeaders,
                             String requestBody,
                             String responseHeaders,
                             String responseContentType,
                             boolean responseBodyTruncated,
                             int responseBodyOriginalLength,
                             int responseBodyStoredLength,
                             String responseBody) {
        this.id = id;
        this.correlationId = correlationId;
        this.clientSessionId = clientSessionId;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
        this.method = method;
        this.path = path;
        this.queryString = queryString;
        this.status = status;
        this.requestContentType = requestContentType;
        this.requestBodyTruncated = requestBodyTruncated;
        this.requestBodyOriginalLength = requestBodyOriginalLength;
        this.requestBodyStoredLength = requestBodyStoredLength;
        this.requestHeaders = requestHeaders;
        this.requestBody = requestBody;
        this.responseHeaders = responseHeaders;
        this.responseContentType = responseContentType;
        this.responseBodyTruncated = responseBodyTruncated;
        this.responseBodyOriginalLength = responseBodyOriginalLength;
        this.responseBodyStoredLength = responseBodyStoredLength;
        this.responseBody = responseBody;
    }
}
