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
public class OllamaToolDefinitionDto {

    @Schema(description = "Type of the tool. Only 'function' is supported currently.", example = "function")
    @Builder.Default
    @NotBlank
    private String type = "function";

    @Schema(description = "Function metadata describing how Ollama should call the backend.")
    @NotNull
    @Valid
    private OllamaToolFunctionDto function;
}
