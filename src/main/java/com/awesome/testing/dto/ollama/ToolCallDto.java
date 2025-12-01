package com.awesome.testing.dto.ollama;

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

    private String id;

    @JsonProperty("function")
    @Valid
    @NotNull
    private ToolCallFunctionDto function;
}
