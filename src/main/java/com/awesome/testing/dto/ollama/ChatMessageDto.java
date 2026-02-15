package com.awesome.testing.dto.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    /**
     * The role of the message sender.
     * Must be either "system", "user" or "assistant".
     */
    @NotBlank
    @Pattern(regexp = "^(system|user|assistant|tool)$", message = "Role must be either 'system', 'user', 'assistant' or 'tool'")
    private String role;

    /**
     * The content of the message.
     * Can be empty when thinking is present (for thinking models).
     */
    private String content;

    /**
     * The thinking content of the message (for thinking models).
     * Present when model is in thinking mode.
     */
    private String thinking;

    /**
     * Tool calls requested by the assistant.
     */
    @JsonProperty("tool_calls")
    @Builder.Default
    private List<ToolCallDto> toolCalls = Collections.emptyList();

    /**
     * Tool name populated when role == tool.
     */
    @JsonProperty("tool_name")
    private String toolName;

    /**
     * Validates that either content, thinking or tool calls are present.
     */
    @AssertTrue(message = "Either content, thinking, or tool calls must be present")
    public boolean isContentOrThinkingOrToolCallPresent() {
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasThinking = thinking != null && !thinking.trim().isEmpty();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        return hasContent || hasThinking || hasToolCalls;
    }

    /**
     * Ensures tool_name is provided when role is "tool".
     */
    @AssertTrue(message = "Tool messages must include tool_name")
    public boolean isToolNamePresentForToolRole() {
        if (!"tool".equals(role)) {
            return true;
        }
        return toolName != null && !toolName.trim().isEmpty();
    }
} 
