package com.awesome.testing.dto.ollama;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.AssertTrue;

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
    @Pattern(regexp = "^(system|user|assistant)$", message = "Role must be either 'system', 'user' or 'assistant'")
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
     * Validates that either content or thinking is present (but both can be present).
     */
    @AssertTrue(message = "Either content or thinking must be present")
    private boolean isContentOrThinkingPresent() {
        return (content != null && !content.trim().isEmpty()) || 
               (thinking != null && !thinking.trim().isEmpty());
    }
} 