package com.awesome.testing.dto.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GenerateResponseDto(
    String model,
    @JsonProperty("created_at") String createdAt,
    String response,
    boolean done,
    Long[] context,
    @JsonProperty("total_duration") Long totalDuration
) {} 