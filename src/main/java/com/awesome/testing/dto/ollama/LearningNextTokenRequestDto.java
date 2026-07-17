package com.awesome.testing.dto.ollama;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningNextTokenRequestDto {

    @NotBlank
    @Size(max = 200)
    @Schema(description = "Installed Ollama model to inspect", example = "llama3.2:1b")
    private String model;

    @NotBlank
    @Size(max = 2000)
    @Schema(description = "Raw prompt whose next-token distribution should be inspected")
    private String prompt;

    @NotNull
    @Min(2)
    @Max(20)
    @Builder.Default
    @Schema(description = "Number of candidate tokens requested from Ollama", defaultValue = "10")
    private Integer topK = 10;
}
