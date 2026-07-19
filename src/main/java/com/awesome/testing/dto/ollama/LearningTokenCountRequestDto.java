package com.awesome.testing.dto.ollama;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningTokenCountRequestDto {

    @NotBlank
    @Size(max = 200)
    @Schema(description = "Installed Ollama generation model", example = "hf.co/prism-ml/Bonsai-27B-gguf:Q1_0")
    private String model;

    @NotBlank
    @Size(max = 2000)
    @Schema(description = "Raw prompt whose tokenizer count should be verified")
    private String prompt;
}
