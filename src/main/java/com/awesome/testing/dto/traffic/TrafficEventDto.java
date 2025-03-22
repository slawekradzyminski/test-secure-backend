package com.awesome.testing.dto.traffic;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TrafficEventDto {
    private String method;
    private String path;
    private int status;
    private long durationMs;
    private Instant timestamp;
} 