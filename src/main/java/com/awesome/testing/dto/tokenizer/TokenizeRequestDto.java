package com.awesome.testing.dto.tokenizer;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for /tokenize endpoint in Python sidecar")
public class TokenizeRequestDto {

    @NotBlank
    @Schema(description = "The input text to split into tokens", example = "Hello world!")
    private String text;

    @JsonProperty("model_name")
    @Schema(description = "The name of the model's tokenizer to use", example = "gpt2", defaultValue = "gpt2")
    private String modelName = "gpt2";
} 