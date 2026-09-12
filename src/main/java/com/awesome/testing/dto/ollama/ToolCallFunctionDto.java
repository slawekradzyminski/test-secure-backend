package com.awesome.testing.dto.ollama;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallFunctionDto {

    @NotBlank
    @Schema(minLength = 1, pattern = "\\S")
    private String name;

    private Map<String, Object> arguments;
}
