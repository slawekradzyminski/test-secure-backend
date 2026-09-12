package com.awesome.testing.dto.ollama;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallDto {

    @Schema(types = {"string", "null"})
    private String id;

    @JsonProperty("function")
    @Valid
    @NotNull
    private ToolCallFunctionDto function;
}
