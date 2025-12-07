package com.awesome.testing.dto.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaToolSchemaPropertyDto {

    @Schema(description = "Property type", example = "string")
    private String type;

    @Schema(description = "Human friendly explanation", example = "Numeric id of the product")
    private String description;

    @Schema(description = "Enum values, when applicable")
    @JsonProperty("enum")
    private List<String> enumValues;
}
