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
@Schema(description = "Response containing token embeddings from the Python sidecar service")
public class EmbeddingsResponseDto {
    @Schema(description = "List of tokens from the tokenized input text", example = "[\"Hello\", \"world\"]")
    private List<String> tokens;
    
    @Schema(description = "Embeddings for each token (list of vectors)", example = "[[0.1, 0.2], [0.3, 0.4]]")
    private List<List<Float>> embeddings;
    
    @JsonProperty("model_name")
    @Schema(description = "Name of the model used for processing", example = "gpt2")
    private String modelName;
} 