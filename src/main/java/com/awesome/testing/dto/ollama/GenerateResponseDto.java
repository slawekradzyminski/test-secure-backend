package com.awesome.testing.dto.ollama;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(types = {"string", "null"})
    private String model;
    
    @JsonProperty("created_at")
    @Schema(types = {"string", "null"})
    private String createdAt;
    
    @Schema(types = {"string", "null"})
    private String response;
    
    /**
     * The thinking content from the model (for thinking models).
     * Present when model is in thinking mode.
     */
    @Schema(types = {"string", "null"})
    private String thinking;
    
    private boolean done;
    @Schema(types = {"array", "null"})
    private Long[] context;
    
    @JsonProperty("total_duration")
    @Schema(types = {"integer", "null"})
    private Long totalDuration;
} 