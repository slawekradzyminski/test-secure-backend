package com.awesome.testing.dto.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateResponseDto {
    private String model;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    private String response;
    
    /**
     * The thinking content from the model (for thinking models).
     * Present when model is in thinking mode.
     */
    private String thinking;
    
    private boolean done;
    private Long[] context;
    
    @JsonProperty("total_duration")
    private Long totalDuration;
} 