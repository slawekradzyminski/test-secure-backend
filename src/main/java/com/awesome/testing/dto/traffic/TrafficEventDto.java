package com.awesome.testing.dto.traffic;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TrafficEventDto {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private ProtocolDetails protocolDetails;
    private String clientSessionId;
    private String method;
    private String path;
    private int status;
    private long durationMs;
    private Instant timestamp;
}
