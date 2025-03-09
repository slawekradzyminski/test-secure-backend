package com.awesome.testing.dto.embeddings;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "Request for processing text through the Python sidecar service")
public class SidecarRequestDto {
    @NotBlank(message = "Text is required")
    @Size(min = 1, max = 10000, message = "Text must be between 1 and 10000 characters")
    @Schema(description = "Text to process", example = "Hello world")
    private String text;

    @JsonProperty("model_name")
    @Schema(description = "Model name to use for processing", example = "gpt2", defaultValue = "gpt2")
    private String modelName = "gpt2";

    @JsonProperty("dimensionality_reduction")
    @Schema(description = "Whether to perform dimensionality reduction on embeddings", example = "false", defaultValue = "false")
    private Boolean dimensionalityReduction = false;

    @JsonProperty("reduction_method")
    @Schema(description = "Method to use for dimensionality reduction", example = "pca", defaultValue = "pca")
    private String reductionMethod = "pca";

    @JsonProperty("n_components")
    @Min(value = 2, message = "Number of components must be at least 2")
    @Max(value = 3, message = "Number of components must be at most 3")
    @Schema(description = "Number of components for dimensionality reduction", example = "2", defaultValue = "2")
    private Integer nComponents = 2;
} 