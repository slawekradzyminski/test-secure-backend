package com.awesome.testing.dto.ollama;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record GenerateRequestDto(
    @NotBlank String model,
    @NotBlank String prompt,
    Boolean stream,
    Map<String, Object> options
) {} 