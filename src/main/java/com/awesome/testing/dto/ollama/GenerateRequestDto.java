package com.awesome.testing.dto.ollama;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateRequestDto {
    @Schema(description = "Model name", example = "gemma:2b")
    @NotBlank String model;
    @Schema(description = "Prompt", example = "Hello, how are you?")
    @NotBlank String prompt;
    @Schema(description = "Stream", example = "true")
    Boolean stream;
    @Schema(description = "Options", example = "{ \"temperature\": 0.5 }")
    Map<String, Object> options;
}