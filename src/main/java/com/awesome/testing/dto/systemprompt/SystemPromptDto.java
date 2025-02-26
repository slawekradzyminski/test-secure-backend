package com.awesome.testing.dto.systemprompt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemPromptDto {
    
    @Size(max = 500)
    @Schema(description = "System prompt for Ollama chat", example = "You are a helpful assistant.")
    private String systemPrompt;
} 