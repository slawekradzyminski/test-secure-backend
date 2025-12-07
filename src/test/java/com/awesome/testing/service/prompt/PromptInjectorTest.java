package com.awesome.testing.service.prompt;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ChatRequestDto;
import com.awesome.testing.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptInjectorTest {

    @Mock
    private UserService userService;

    private PromptInjector promptInjector;

    @BeforeEach
    void setUp() {
        promptInjector = new PromptInjector(userService);
    }

    @Test
    void shouldPrependChatPrompt() {
        ChatRequestDto request = ChatRequestDto.builder()
                .model("qwen3")
                .messages(List.of(
                        ChatMessageDto.builder().role("user").content("Hello").build()
                ))
                .options(Map.of("temperature", 0.3))
                .think(true)
                .build();
        when(userService.getChatSystemPrompt("alice")).thenReturn("Chat Prompt");

        ChatRequestDto result = promptInjector.augmentChatRequest("alice", request);

        assertThat(result.getMessages()).hasSize(2);
        assertThat(result.getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(result.getMessages().get(0).getContent()).isEqualTo("Chat Prompt");
        assertThat(result.getMessages().get(1).getContent()).isEqualTo("Hello");
        assertThat(result.getOptions()).containsEntry("temperature", 0.3);
        assertThat(result.getThink()).isTrue();
    }

    @Test
    void shouldPrependChatAndToolPromptsForToolRequests() {
        ChatRequestDto request = ChatRequestDto.builder()
                .model("qwen3")
                .messages(List.of(ChatMessageDto.builder().role("user").content("Compare items").build()))
                .tools(List.of())
                .build();
        when(userService.getChatSystemPrompt("bob")).thenReturn("Chat Prompt");
        when(userService.getToolSystemPrompt("bob")).thenReturn("Tool Prompt");

        ChatRequestDto result = promptInjector.augmentToolRequest("bob", request);

        assertThat(result.getMessages()).hasSize(3);
        assertThat(result.getMessages().get(0).getContent()).isEqualTo("Chat Prompt");
        assertThat(result.getMessages().get(1).getContent()).isEqualTo("Tool Prompt");
        assertThat(result.getMessages().get(2).getContent()).isEqualTo("Compare items");
    }

    @Test
    void shouldDeduplicateExistingPrompts() {
        ChatRequestDto request = ChatRequestDto.builder()
                .model("qwen3")
                .messages(List.of(
                        ChatMessageDto.builder().role("system").content("Chat Prompt").build(),
                        ChatMessageDto.builder().role("system").content("Tool Prompt").build(),
                        ChatMessageDto.builder().role("user").content("Hi").build()
                ))
                .build();
        when(userService.getChatSystemPrompt("carol")).thenReturn("Chat Prompt");
        when(userService.getToolSystemPrompt("carol")).thenReturn("Tool Prompt");

        ChatRequestDto result = promptInjector.augmentToolRequest("carol", request);

        assertThat(result.getMessages()).hasSize(3);
        assertThat(result.getMessages().get(0).getContent()).isEqualTo("Chat Prompt");
        assertThat(result.getMessages().get(1).getContent()).isEqualTo("Tool Prompt");
        assertThat(result.getMessages().get(2).getContent()).isEqualTo("Hi");
    }
}
