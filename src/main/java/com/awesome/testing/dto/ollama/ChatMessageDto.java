package com.awesome.testing.dto.ollama;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     */
    @NotBlank
    private String content;
} 