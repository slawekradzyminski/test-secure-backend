package com.awesome.testing.dto.embeddings;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request for processing text to get attention weights")
public class AttentionRequestDto {
    @NotBlank(message = "Text is required")
    @Size(min = 1, max = 10000, message = "Text must be between 1 and 10000 characters")
    @Schema(description = "Text to process", example = "Hello world")
    private String text;

    @JsonProperty("model_name")
    @Schema(description = "Model name to use for processing", example = "gpt2", defaultValue = "gpt2")
    private String modelName = "gpt2";
} 