package com.awesome.testing.dto.ollama;

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
    private List<ChatMessageDto> messages;

    /**
     * Optional model parameters (e.g., temperature, maxTokens, etc.)
     */
    private Map<String, Object> options;

    /**
     * Whether to stream the response. Defaults to true if not specified.
     */
    private Boolean stream;

    /**
     * How long to keep the model loaded in memory (in minutes).
     */
    private Integer keepAlive;
} 