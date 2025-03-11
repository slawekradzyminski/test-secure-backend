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
@Schema(description = "Response containing attention weights from the Python sidecar service")
public class AttentionResponseDto {
    @Schema(description = "List of tokens from the tokenized input text", example = "[\"Hello\", \"world\"]")
    private List<String> tokens;
    
    @Schema(description = "Attention weights from the model (shape: [layers, heads, seq_len, seq_len])")
    private List<List<List<List<Float>>>> attention;
    // shape = [layers, heads, seq_len, seq_len] typically
    
    @JsonProperty("model_name")
    @Schema(description = "Name of the model used for processing", example = "gpt2")
    private String modelName;
} 