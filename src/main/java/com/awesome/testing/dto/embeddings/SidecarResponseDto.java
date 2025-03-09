package com.awesome.testing.dto.embeddings;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "Response from the Python sidecar service containing embeddings and attention data")
public class SidecarResponseDto {
    @Schema(description = "List of tokens from the tokenized input text", example = "[\"Hello\", \"world\"]")
    private List<String> tokens;
    
    @Schema(description = "Embeddings for each token (list of vectors)", example = "[[0.1, 0.2], [0.3, 0.4]]")
    private List<List<Float>> embeddings;
    
    @Schema(description = "Attention weights from the model (shape: [layers, heads, seq_len, seq_len])")
    private List<List<List<List<Float>>>> attention;
    // shape = [layers, heads, seq_len, seq_len] typically

    @JsonProperty("reduced_embeddings")
    @Schema(description = "Dimensionally reduced embeddings (if requested)", example = "[[0.5, 0.6], [0.7, 0.8]]")
    private List<List<Double>> reducedEmbeddings; // optional

    @JsonProperty("model_name")
    @Schema(description = "Name of the model used for processing", example = "gpt2")
    private String modelName;
} 