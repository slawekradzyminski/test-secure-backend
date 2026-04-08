package com.awesome.testing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "traffic_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrafficLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_id", nullable = false, length = 64, unique = true)
    private String correlationId;

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

    @Lob
    @Column(name = "request_headers")
    private String requestHeaders;

    @Lob
    @Column(name = "request_body")
    private String requestBody;

    @Lob
    @Column(name = "response_headers")
    private String responseHeaders;

    @Lob
    @Column(name = "response_body")
    private String responseBody;
}
