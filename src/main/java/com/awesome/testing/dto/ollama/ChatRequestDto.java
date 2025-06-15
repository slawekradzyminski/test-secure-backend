package com.awesome.testing.dto.ollama;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDto {
    @NotBlank
    @Schema(
            description = "Model to use. Only this model is downloaded automatically. " +
                    "Other model have to be manually downloaded on Ollama server",
            example = "qwen3:0.6b"
    )
    private String model;

    /**
     * Complete conversation history in chronological order.
     * Each request should include all previous messages to maintain context.
     * Example sequence:
     * 1. [user: "Hi"]
     * 2. [user: "Hi", assistant: "Hello!", user: "How are you?"]
     * 3. [user: "Hi", assistant: "Hello!", user: "How are you?", assistant: "I'm good!", user: "Great!"]
     */
    @NotEmpty(message = "At least one message is required")
    @Schema(
            description = "Complete conversation history in chronological order.",
            example = """
                    [
                      { "role": "system", "content": "You are a helpful AI assistant. You must use the conversation history to answer questions." },
                      { "role": "user", "content": "I love programming in Python." },
                      { "role": "assistant", "content": "That's great! Python is a versatile and popular programming language. What do you enjoy most about Python programming?" },
                      { "role": "user", "content": "What programming language did I say I love?" }
                    ]"""
    )
    private List<ChatMessageDto> messages;

    /**
     * Optional model parameters (e.g., temperature, maxTokens, etc.)
     */
    @Schema(description = "Options", example = "{ \"temperature\": 0.7 }")
    private Map<String, Object> options;

    /**
     * Whether to stream the response.
     * Defaults to true.
     * Hidden from Swagger documentation.
     */
    @Schema(hidden = true)
    @Builder.Default
    private Boolean stream = true;

    /**
     * How long to keep the model loaded in memory (in minutes).
     * Defaults to 10000.
     * Hidden from Swagger documentation.
     */
    @Schema(hidden = true)
    @Builder.Default
    private Integer keepAlive = 10000;
} 