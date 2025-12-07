package com.awesome.testing.dto.systemprompt;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolSystemPromptDto {

    @Size(max = 5000, message = "Tool system prompt must be at most 5000 characters")
    @Schema(description = "Tool-specific system prompt for function calling", example = "Always call get_product_snapshot before answering.")
    private String toolSystemPrompt;
}
