package com.awesome.testing.dto.embeddings;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response containing dimensionally reduced embeddings from the Python sidecar service")
public class ReduceResponseDto {
    @Schema(description = "List of tokens from the tokenized input text", example = "[\"Hello\", \"world\"]")
    private List<String> tokens;
    
    @JsonProperty("reduced_embeddings")
    @Schema(description = "Dimensionally reduced embeddings (typically 2D or 3D)", example = "[[0.5, 0.6], [0.7, 0.8]]")
    private List<List<Double>> reducedEmbeddings;
    
    @JsonProperty("model_name")
    @Schema(description = "Name of the model used for processing", example = "gpt2")
    private String modelName;
} 