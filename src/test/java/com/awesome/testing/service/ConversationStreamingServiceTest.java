package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.conversation.ConversationChatRequestDto;
import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ChatResponseDto;
import com.awesome.testing.dto.ollama.ToolCallDto;
import com.awesome.testing.dto.ollama.ToolCallFunctionDto;
import com.awesome.testing.entity.ConversationEntity;
import com.awesome.testing.entity.ConversationMessageEntity;
import com.awesome.testing.repository.ConversationMessageRepository;
import com.awesome.testing.repository.ConversationRepository;
import com.awesome.testing.service.conversation.ConversationMessageMapper;
import com.awesome.testing.service.ollama.OllamaFunctionCallingService;
import com.awesome.testing.service.ollama.OllamaService;
import com.awesome.testing.service.ollama.OllamaToolDefinitionCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationStreamingServiceTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @Mock
    private OllamaService ollamaService;

    @Mock
    private OllamaFunctionCallingService ollamaFunctionCallingService;

    @Mock
    private OllamaToolDefinitionCatalog toolDefinitionCatalog;

    private ConversationStreamingService conversationStreamingService;

    @BeforeEach
    void setUp() {
        conversationStreamingService = new ConversationStreamingService(
                conversationRepository,
                conversationMessageRepository,
                new ConversationMessageMapper(new ObjectMapper()),
                ollamaService,
                ollamaFunctionCallingService,
                toolDefinitionCatalog
        );
    }

    @Test
    void shouldPersistAssistantMessageForChatConversation() {
        ConversationEntity conversation = baseConversation(ConversationType.CHAT);
        ConversationMessageEntity systemMessage = systemMessage(conversation);
        List<ConversationMessageEntity> persistedMessages = new ArrayList<>();
        when(conversationRepository.findByIdAndOwnerUsernameAndArchivedFalse(CONVERSATION_ID, "alice"))
                .thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.countByConversation(conversation)).thenReturn(1L);
        when(conversationMessageRepository.save(any())).thenAnswer(invocation -> {
            ConversationMessageEntity entity = invocation.getArgument(0);
            persistedMessages.add(entity);
            return entity;
        });
        when(conversationMessageRepository.findByConversationOrderByPositionAsc(conversation))
                .thenAnswer(invocation -> {
                    List<ConversationMessageEntity> history = new ArrayList<>();
                    history.add(systemMessage);
                    history.addAll(persistedMessages);
                    return history;
                });

        ChatResponseDto chunk = ChatResponseDto.builder()
                .model("qwen3:0.6b")
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("Hello Alice")
                        .build())
                .done(true)
                .build();
        when(ollamaService.chat(any())).thenReturn(Flux.just(chunk));

        StepVerifier.create(conversationStreamingService.chat("alice", CONVERSATION_ID, request("Hi")))
                .expectNext(chunk)
                .verifyComplete();

        assertThat(persistedMessages).hasSize(2);
        ConversationMessageEntity userMessage = persistedMessages.get(0);
        assertThat(userMessage.getRole()).isEqualTo("user");
        assertThat(userMessage.getPosition()).isEqualTo(1);

        ConversationMessageEntity assistantMessage = persistedMessages.get(1);
        assertThat(assistantMessage.getRole()).isEqualTo("assistant");
        assertThat(assistantMessage.getContent()).isEqualTo("Hello Alice");
    }

    @Test
    void shouldPersistToolCallsAndResponses() {
        ConversationEntity conversation = baseConversation(ConversationType.TOOL);
        ConversationMessageEntity systemMessage = systemMessage(conversation);
        List<ConversationMessageEntity> persistedMessages = new ArrayList<>();
        when(conversationRepository.findByIdAndOwnerUsernameAndArchivedFalse(CONVERSATION_ID, "alice"))
                .thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.countByConversation(conversation)).thenReturn(1L);
        when(conversationMessageRepository.save(any())).thenAnswer(invocation -> {
            ConversationMessageEntity entity = invocation.getArgument(0);
            persistedMessages.add(entity);
            return entity;
        });
        when(conversationMessageRepository.findByConversationOrderByPositionAsc(conversation))
                .thenAnswer(invocation -> {
                    List<ConversationMessageEntity> history = new ArrayList<>();
                    history.add(systemMessage);
                    history.addAll(persistedMessages);
                    return history;
                });
        when(toolDefinitionCatalog.getDefinitions()).thenReturn(List.of());

        ToolCallDto toolCall = ToolCallDto.builder()
                .id("call-1")
                .function(ToolCallFunctionDto.builder()
                        .name("get_product_snapshot")
                        .arguments(Map.of("productId", 1))
                        .build())
                .build();

        ChatResponseDto assistantToolRequest = ChatResponseDto.builder()
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .toolCalls(List.of(toolCall))
                        .build())
                .done(false)
                .build();

        ChatResponseDto toolResponse = ChatResponseDto.builder()
                .message(ChatMessageDto.builder()
                        .role("tool")
                        .toolName("get_product_snapshot")
                        .content("{\"id\":1}")
                        .build())
                .done(false)
                .build();

        ChatResponseDto assistantFinal = ChatResponseDto.builder()
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("Retro Console costs $99")
                        .build())
                .done(true)
                .build();

        when(ollamaFunctionCallingService.chatWithTools(any()))
                .thenReturn(Flux.just(assistantToolRequest, toolResponse, assistantFinal));

        StepVerifier.create(conversationStreamingService.chatWithTools("alice", CONVERSATION_ID, request("price?")))
                .expectNext(assistantToolRequest, toolResponse, assistantFinal)
                .verifyComplete();

        assertThat(persistedMessages).hasSize(4); // user + tool_call + tool + assistant
        assertThat(persistedMessages.get(1).getRole()).isEqualTo("assistant");
        assertThat(persistedMessages.get(1).getToolCallsJson()).isNotBlank();
        assertThat(persistedMessages.get(2).getRole()).isEqualTo("tool");
        assertThat(persistedMessages.get(3).getContent()).contains("Retro Console");
    }

    @Test
    void shouldFailWhenConversationMissing() {
        when(conversationRepository.findByIdAndOwnerUsernameAndArchivedFalse(CONVERSATION_ID, "alice"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationStreamingService.chat("alice", CONVERSATION_ID, request("hi")))
                .isInstanceOf(CustomException.class);
    }

    private static ConversationChatRequestDto request(String content) {
        return ConversationChatRequestDto.builder()
                .content(content)
                .build();
    }

    private static ConversationEntity baseConversation(ConversationType type) {
        return ConversationEntity.builder()
                .id(CONVERSATION_ID)
                .type(type)
                .title("Thread")
                .model("qwen3:0.6b")
                .temperature(0.6)
                .think(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private static ConversationMessageEntity systemMessage(ConversationEntity conversation) {
        return ConversationMessageEntity.builder()
                .conversation(conversation)
                .position(0)
                .role("system")
                .content("System prompt")
                .build();
    }
}
