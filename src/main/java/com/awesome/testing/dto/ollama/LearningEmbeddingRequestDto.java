package com.awesome.testing.dto.ollama;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningEmbeddingRequestDto {

    @NotBlank
    @Size(max = 200)
    @Schema(description = "Installed Ollama embedding model", example = "embeddinggemma")
    private String model;

    @NotNull
    @Size(min = 2, max = 8)
    @Schema(description = "Two to eight short texts to embed and compare")
    private List<@NotBlank @Size(max = 500) String> inputs;
}
