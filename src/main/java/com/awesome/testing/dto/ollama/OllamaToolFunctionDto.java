package com.awesome.testing.dto.ollama;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaToolFunctionDto {

    @Schema(description = "Function name exposed to the model", example = "get_product_snapshot")
    @NotBlank
    private String name;

    @Schema(description = "Short human-readable description", example = "Return catalog metadata for a product")
    private String description;

    @Schema(description = "JSON schema describing the function arguments")
    @Valid
    @NotNull
    private OllamaToolParametersDto parameters;
}
