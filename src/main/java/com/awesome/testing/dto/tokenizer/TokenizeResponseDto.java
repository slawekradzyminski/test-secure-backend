package com.awesome.testing.dto.tokenizer;

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
@Schema(description = "Response from /tokenize endpoint in Python sidecar")
public class TokenizeResponseDto {

    @Schema(description = "List of tokens derived from the input text")
    private List<String> tokens;

    @JsonProperty("model_name")
    @Schema(description = "Name of the model used", example = "gpt2")
    private String modelName;
}
