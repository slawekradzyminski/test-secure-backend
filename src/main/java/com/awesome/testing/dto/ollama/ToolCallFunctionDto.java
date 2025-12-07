package com.awesome.testing.dto.ollama;

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
    private String name;

    private Map<String, Object> arguments;
}
