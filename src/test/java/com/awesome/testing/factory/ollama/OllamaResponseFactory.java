package com.awesome.testing.factory.ollama;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ChatResponseDto;
import com.awesome.testing.dto.ollama.GenerateResponseDto;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OllamaResponseFactory {

    private static final String DEFAULT_MODEL = "qwen3:0.6b";
    private static final String DEFAULT_DATE = "2024-02-21";

    // Generate Response DTOs
    public static GenerateResponseDto simpleGenerateResponse() {
        return GenerateResponseDto.builder()
                .model(DEFAULT_MODEL)
                .createdAt(DEFAULT_DATE)
                .response("Hello")
                .thinking(null)
                .done(false)
                .context(null)
                .totalDuration(100L)
                .build();
    }

    public static GenerateResponseDto finalGenerateResponse() {
        return GenerateResponseDto.builder()
                .model(DEFAULT_MODEL)
                .createdAt(DEFAULT_DATE)
                .response("World")
                .thinking(null)
                .done(true)
                .context(null)
                .totalDuration(200L)
                .build();
    }

    public static GenerateResponseDto thinkingGenerateResponse() {
        return GenerateResponseDto.builder()
                .model(DEFAULT_MODEL)
                .createdAt(DEFAULT_DATE)
                .response("")
                .thinking("Let me think about this...")
                .done(false)
                .build();
    }

    public static GenerateResponseDto contentGenerateResponse() {
        return GenerateResponseDto.builder()
                .model(DEFAULT_MODEL)
                .createdAt(DEFAULT_DATE)
                .response("Here's my answer")
                .thinking("")
                .done(true)
                .build();
    }

    public static GenerateResponseDto completedGenerateResponse() {
        return GenerateResponseDto.builder()
                .model(DEFAULT_MODEL)
                .createdAt(DEFAULT_DATE)
                .response("Hello")
                .thinking(null)
                .done(true)
                .context(null)
                .totalDuration(100L)
                .build();
    }

    // Chat Response DTOs
    public static ChatResponseDto simpleChatResponse() {
        return ChatResponseDto.builder()
                .model(DEFAULT_MODEL)
                .createdAt(DEFAULT_DATE)
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("Hi")
                        .build())
                .done(false)
                .build();
    }

    public static ChatResponseDto finalChatResponse() {
        return ChatResponseDto.builder()
                .model(DEFAULT_MODEL)
                .createdAt(DEFAULT_DATE)
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("there!")
                        .build())
                .done(true)
                .build();
    }

    public static ChatResponseDto thinkingChatResponse() {
        return ChatResponseDto.builder()
                .model(DEFAULT_MODEL)
                .createdAt(DEFAULT_DATE)
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("")
                        .thinking("Let me think about this...")
                        .build())
                .done(false)
                .build();
    }

    public static ChatResponseDto contentChatResponse() {
        return ChatResponseDto.builder()
                .model(DEFAULT_MODEL)
                .createdAt(DEFAULT_DATE)
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("Here's my answer")
                        .thinking("")
                        .build())
                .done(true)
                .build();
    }

    public static ChatResponseDto completedChatResponse() {
        return ChatResponseDto.builder()
                .model(DEFAULT_MODEL)
                .createdAt(DEFAULT_DATE)
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("Hi")
                        .build())
                .done(true)
                .build();
    }
} 