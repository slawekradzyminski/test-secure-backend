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
public class StreamedRequestDto {
    @NotBlank
    @Schema(description = "Model to use, needs to be downloaded in ollama server", example = "qwen3:4b-instruct")
    String model;

    @Schema(description = "Prompt", example = "Hello, how are you?")
    @NotBlank
    String prompt;

    @Schema(description = "Options", example = "{ \"temperature\": 0.5 }")
    Map<String, Object> options;

    @Schema(description = "Should the model think before responding?", example = "false")
    @Builder.Default
    Boolean think = false;
}