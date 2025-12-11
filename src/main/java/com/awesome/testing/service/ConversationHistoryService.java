package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.conversation.ConversationDetailDto;
import com.awesome.testing.dto.conversation.ConversationSummaryDto;
import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.dto.conversation.CreateConversationRequestDto;
import com.awesome.testing.dto.conversation.UpdateConversationRequestDto;
import com.awesome.testing.entity.ConversationEntity;
import com.awesome.testing.entity.ConversationMessageEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.ConversationMessageRepository;
import com.awesome.testing.repository.ConversationRepository;
import com.awesome.testing.service.conversation.ConversationMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ConversationHistoryService {

    private static final String DEFAULT_CHAT_MODEL = "qwen3:0.6b";
    private static final String DEFAULT_TOOL_MODEL = "qwen3:4b-instruct";
    private static final double DEFAULT_CHAT_TEMPERATURE = 0.8d;
    private static final double DEFAULT_TOOL_TEMPERATURE = 0.4d;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final UserService userService;
    private final ConversationMessageMapper messageMapper;

    public List<ConversationSummaryDto> listConversations(String username, ConversationType type) {
        List<ConversationEntity> entities = type == null
                ? conversationRepository.findByOwnerUsernameAndArchivedFalseOrderByUpdatedAtDesc(username)
                : conversationRepository.findByOwnerUsernameAndTypeAndArchivedFalseOrderByUpdatedAtDesc(username, type);
        return entities.stream()
                .map(this::toSummary)
                .toList();
    }

    public ConversationDetailDto createConversation(String username, CreateConversationRequestDto request) {
        UserEntity owner = userService.search(username);
        ConversationType type = request.getType();
        String systemPrompt = resolveSystemPrompt(username, type);

        ConversationEntity conversation = ConversationEntity.builder()
                .owner(owner)
                .type(type)
                .title(resolveTitle(request.getTitle(), type))
                .model(resolveModel(request.getModel(), type))
                .temperature(resolveTemperature(request.getTemperature(), type))
                .think(Boolean.TRUE.equals(request.getThink()))
                .systemPromptSnapshot(systemPrompt)
                .build();

        ConversationEntity saved = conversationRepository.save(conversation);

        ConversationMessageEntity systemMessage = ConversationMessageEntity.builder()
                .conversation(saved)
                .position(0)
                .role("system")
                .content(systemPrompt)
                .build();
        conversationMessageRepository.save(systemMessage);

        return ConversationDetailDto.builder()
                .summary(toSummary(saved))
                .systemPromptSnapshot(systemPrompt)
                .messages(List.of(messageMapper.toDto(systemMessage)))
                .build();
    }

    public ConversationDetailDto getConversation(String username, UUID conversationId) {
        ConversationEntity conversation = getOwnedConversation(username, conversationId);
        List<ConversationMessageEntity> messages = conversationMessageRepository
                .findByConversationOrderByPositionAsc(conversation);

        return ConversationDetailDto.builder()
                .summary(toSummary(conversation))
                .systemPromptSnapshot(conversation.getSystemPromptSnapshot())
                .messages(messages.stream().map(messageMapper::toDto).toList())
                .build();
    }

    public ConversationSummaryDto updateConversation(String username,
                                                     UUID conversationId,
                                                     UpdateConversationRequestDto request) {
        ConversationEntity conversation = getOwnedConversation(username, conversationId);
        boolean dirty = false;
        if (StringUtils.hasText(request.getTitle())
                && !request.getTitle().equals(conversation.getTitle())) {
            conversation.setTitle(request.getTitle().trim());
            dirty = true;
        }
        if (request.getArchived() != null && request.getArchived() != conversation.isArchived()) {
            conversation.setArchived(request.getArchived());
            dirty = true;
        }
        if (!dirty) {
            return toSummary(conversation);
        }
        ConversationEntity saved = conversationRepository.save(conversation);
        return toSummary(saved);
    }

    public void archiveConversation(String username, UUID conversationId) {
        ConversationEntity conversation = getOwnedConversation(username, conversationId);
        conversation.setArchived(true);
        conversationRepository.save(conversation);
    }

    private ConversationEntity getOwnedConversation(String username, UUID conversationId) {
        return conversationRepository.findByIdAndOwnerUsernameAndArchivedFalse(conversationId, username)
                .orElseThrow(() -> new CustomException("Conversation not found", HttpStatus.NOT_FOUND));
    }

    private String resolveSystemPrompt(String username, ConversationType type) {
        return switch (type) {
            case CHAT -> userService.getChatSystemPrompt(username);
            case TOOL -> userService.getToolSystemPrompt(username);
        };
    }

    private String resolveTitle(String requested, ConversationType type) {
        if (StringUtils.hasText(requested)) {
            return requested.trim();
        }
        return type == ConversationType.TOOL ? "New tool chat" : "New chat";
    }

    private String resolveModel(String requested, ConversationType type) {
        if (StringUtils.hasText(requested)) {
            return requested.trim();
        }
        return type == ConversationType.TOOL ? DEFAULT_TOOL_MODEL : DEFAULT_CHAT_MODEL;
    }

    private double resolveTemperature(Double requested, ConversationType type) {
        if (requested != null) {
            return requested;
        }
        return type == ConversationType.TOOL ? DEFAULT_TOOL_TEMPERATURE : DEFAULT_CHAT_TEMPERATURE;
    }

    private ConversationSummaryDto toSummary(ConversationEntity entity) {
        return ConversationSummaryDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .type(entity.getType())
                .model(entity.getModel())
                .temperature(entity.getTemperature())
                .think(entity.getThink())
                .archived(entity.isArchived())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
