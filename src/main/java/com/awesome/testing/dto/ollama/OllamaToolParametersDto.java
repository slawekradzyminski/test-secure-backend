package com.awesome.testing.dto.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaToolParametersDto {

    @Schema(description = "JSON schema type. Usually 'object'.", example = "object")
    @NotBlank
    private String type;

    @Schema(description = "JSON schema properties describing the arguments.")
    @NotEmpty
    @Valid
    @Builder.Default
    private Map<String, OllamaToolSchemaPropertyDto> properties = Collections.emptyMap();

    @Schema(description = "List of required properties for this schema.")
    private List<String> required;

    @Schema(description = "Optional oneOf blocks to express alternative required sets.")
    @JsonProperty("oneOf")
    private List<OllamaToolParametersRequirementDto> oneOf;
}
