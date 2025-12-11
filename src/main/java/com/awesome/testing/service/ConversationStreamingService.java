package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.conversation.ConversationChatRequestDto;
import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ChatRequestDto;
import com.awesome.testing.dto.ollama.ChatResponseDto;
import com.awesome.testing.entity.ConversationEntity;
import com.awesome.testing.entity.ConversationMessageEntity;
import com.awesome.testing.repository.ConversationMessageRepository;
import com.awesome.testing.repository.ConversationRepository;
import com.awesome.testing.service.conversation.ConversationMessageMapper;
import com.awesome.testing.service.ollama.OllamaFunctionCallingService;
import com.awesome.testing.service.ollama.OllamaService;
import com.awesome.testing.service.ollama.OllamaToolDefinitionCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ConversationStreamingService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final ConversationMessageMapper messageMapper;
    private final OllamaService ollamaService;
    private final OllamaFunctionCallingService ollamaFunctionCallingService;
    private final OllamaToolDefinitionCatalog toolDefinitionCatalog;

    public Flux<ChatResponseDto> chat(String username,
                                      UUID conversationId,
                                      ConversationChatRequestDto request) {
        ConversationEntity conversation = getOwnedConversation(username, conversationId);
        ensureConversationType(conversation, ConversationType.CHAT);
        return streamConversation(conversation, request, false);
    }

    public Flux<ChatResponseDto> chatWithTools(String username,
                                               UUID conversationId,
                                               ConversationChatRequestDto request) {
        ConversationEntity conversation = getOwnedConversation(username, conversationId);
        ensureConversationType(conversation, ConversationType.TOOL);
        return streamConversation(conversation, request, true);
    }

    private Flux<ChatResponseDto> streamConversation(ConversationEntity conversation,
                                                     ConversationChatRequestDto request,
                                                     boolean toolMode) {
        applyOverrides(conversation, request);
        long nextPosition = appendUserMessage(conversation, request.getContent());
        List<ChatMessageDto> history = conversationMessageRepository.findByConversationOrderByPositionAsc(conversation)
                .stream()
                .map(messageMapper::toDto)
                .toList();

        ChatRequestDto chatRequest = ChatRequestDto.builder()
                .model(conversation.getModel())
                .messages(history)
                .options(resolveOptions(conversation.getTemperature(), request.getOptions()))
                .tools(toolMode ? toolDefinitionCatalog.getDefinitions() : List.of())
                .think(resolveThink(conversation))
                .build();

        Flux<ChatResponseDto> source = toolMode
                ? ollamaFunctionCallingService.chatWithTools(chatRequest)
                : ollamaService.chat(chatRequest);

        AtomicLong positionCounter = new AtomicLong(nextPosition);
        AssistantMessageBuffer assistantBuffer = new AssistantMessageBuffer();

        return source.doOnNext(resp -> handleChunk(conversation, resp, positionCounter, assistantBuffer))
                .doOnComplete(() -> flushAssistant(conversation, positionCounter, assistantBuffer, null))
                .doOnError(error -> flushAssistant(conversation, positionCounter, assistantBuffer, error));
    }

    private void handleChunk(ConversationEntity conversation,
                             ChatResponseDto chunk,
                             AtomicLong positionCounter,
                             AssistantMessageBuffer buffer) {
        ChatMessageDto message = chunk.getMessage();
        if (message == null) {
            if (chunk.isDone()) {
                flushAssistant(conversation, positionCounter, buffer, null);
                buffer.reset();
            }
            return;
        }

        if ("tool".equals(message.getRole())) {
            persistMessage(conversation, message, positionCounter.getAndIncrement());
            buffer.reset();
            return;
        }

        if (!CollectionUtils.isEmpty(message.getToolCalls())) {
            persistMessage(conversation, message, positionCounter.getAndIncrement());
            buffer.reset();
            return;
        }

        buffer.append(message);
        if (chunk.isDone()) {
            flushAssistant(conversation, positionCounter, buffer, null);
            buffer.reset();
        }
    }

    private void flushAssistant(ConversationEntity conversation,
                                AtomicLong positionCounter,
                                AssistantMessageBuffer buffer,
                                Throwable error) {
        if (buffer.isEmpty() && error == null) {
            return;
        }
        ChatMessageDto.ChatMessageDtoBuilder builder = ChatMessageDto.builder().role("assistant");
        if (!buffer.isEmpty()) {
            builder.content(buffer.content());
            builder.thinking(buffer.thinking());
        } else if (error != null) {
            builder.content("Response failed: " + error.getMessage());
        }
        ChatMessageDto dto = builder.build();
        if (error != null && (dto.getContent() == null || dto.getContent().isBlank())) {
            dto = ChatMessageDto.builder()
                    .role("assistant")
                    .content("Response failed")
                    .build();
        }
        persistMessage(conversation, dto, positionCounter.getAndIncrement());
        buffer.reset();
    }

    private void persistMessage(ConversationEntity conversation,
                                ChatMessageDto message,
                                long position) {
        ConversationMessageEntity entity = ConversationMessageEntity.builder()
                .conversation(conversation)
                .position(position)
                .role(message.getRole())
                .content(message.getContent())
                .thinking(message.getThinking())
                .toolName(message.getToolName())
                .build();
        messageMapper.applyToolCalls(entity, message.getToolCalls());
        conversationMessageRepository.save(entity);
        conversation.setUpdatedAt(Instant.now());
    }

    private long appendUserMessage(ConversationEntity conversation, String content) {
        long position = conversationMessageRepository.countByConversation(conversation);
        ConversationMessageEntity entity = ConversationMessageEntity.builder()
                .conversation(conversation)
                .position(position)
                .role("user")
                .content(content)
                .build();
        conversationMessageRepository.save(entity);
        conversation.setUpdatedAt(Instant.now());
        return position + 1;
    }

    private void applyOverrides(ConversationEntity conversation, ConversationChatRequestDto request) {
        boolean dirty = false;
        if (request.getModel() != null && !request.getModel().isBlank()
                && !request.getModel().equals(conversation.getModel())) {
            conversation.setModel(request.getModel().trim());
            dirty = true;
        }
        if (request.getTemperature() != null
                && !request.getTemperature().equals(conversation.getTemperature())) {
            conversation.setTemperature(request.getTemperature());
            dirty = true;
        }
        if (request.getThink() != null
                && !request.getThink().equals(conversation.getThink())) {
            conversation.setThink(request.getThink());
            dirty = true;
        }
        if (dirty) {
            conversationRepository.save(conversation);
        }
    }

    private Map<String, Object> resolveOptions(Double baseTemperature, Map<String, Object> overrides) {
        Map<String, Object> options = new HashMap<>();
        if (baseTemperature != null) {
            options.put("temperature", baseTemperature);
        }
        if (overrides != null) {
            options.putAll(overrides);
        }
        return options.isEmpty() ? null : options;
    }

    private boolean resolveThink(ConversationEntity conversation) {
        return Boolean.TRUE.equals(conversation.getThink());
    }

    private ConversationEntity getOwnedConversation(String username, UUID conversationId) {
        return conversationRepository.findByIdAndOwnerUsernameAndArchivedFalse(conversationId, username)
                .orElseThrow(() -> new CustomException("Conversation not found", HttpStatus.NOT_FOUND));
    }

    private static void ensureConversationType(ConversationEntity conversation, ConversationType expected) {
        if (conversation.getType() != expected) {
            throw new CustomException("Conversation type mismatch", HttpStatus.BAD_REQUEST);
        }
    }

    private static final class AssistantMessageBuffer {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();

        void append(ChatMessageDto message) {
            if (message.getContent() != null) {
                content.append(message.getContent());
            }
            if (message.getThinking() != null) {
                thinking.append(message.getThinking());
            }
        }

        boolean isEmpty() {
            return content.length() == 0 && thinking.length() == 0;
        }

        String content() {
            return content.toString();
        }

        String thinking() {
            return thinking.toString();
        }

        void reset() {
            content.setLength(0);
            thinking.setLength(0);
        }
    }
}
