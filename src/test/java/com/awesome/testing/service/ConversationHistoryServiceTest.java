package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.conversation.ConversationDetailDto;
import com.awesome.testing.dto.conversation.ConversationSummaryDto;
import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.dto.conversation.CreateConversationRequestDto;
import com.awesome.testing.dto.conversation.UpdateConversationRequestDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.ConversationEntity;
import com.awesome.testing.entity.ConversationMessageEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.ConversationMessageRepository;
import com.awesome.testing.repository.ConversationRepository;
import com.awesome.testing.service.conversation.ConversationMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationHistoryServiceTest {

    private static final String USERNAME = "alice";

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @Mock
    private UserService userService;

    private ConversationHistoryService conversationHistoryService;

    @BeforeEach
    void setUp() {
        conversationHistoryService = new ConversationHistoryService(
                conversationRepository,
                conversationMessageRepository,
                userService,
                new ConversationMessageMapper(new ObjectMapper())
        );
    }

    @Test
    void shouldCreateConversationWithDefaultsAndSystemMessage() {
        UserEntity user = sampleUser();
        when(userService.search(USERNAME)).thenReturn(user);
        when(userService.getChatSystemPrompt(USERNAME)).thenReturn("system prompt");
        when(conversationRepository.save(any())).thenAnswer(invocation -> {
            ConversationEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(entity.getCreatedAt());
            return entity;
        });
        when(conversationMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateConversationRequestDto request = CreateConversationRequestDto.builder()
                .type(ConversationType.CHAT)
                .build();

        ConversationDetailDto result = conversationHistoryService.createConversation(USERNAME, request);

        assertThat(result.getSummary().getTitle()).isEqualTo("New chat");
        assertThat(result.getSummary().getModel()).isEqualTo("qwen3:0.6b");
        assertThat(result.getSystemPromptSnapshot()).isEqualTo("system prompt");
        assertThat(result.getMessages()).hasSize(1);
        assertThat(result.getMessages().getFirst().getRole()).isEqualTo("system");

        ArgumentCaptor<ConversationMessageEntity> captor = ArgumentCaptor.forClass(ConversationMessageEntity.class);
        verify(conversationMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getPosition()).isZero();
    }

    @Test
    void shouldListConversationsByType() {
        ConversationEntity entity = ConversationEntity.builder()
                .id(UUID.randomUUID())
                .title("Tool thread")
                .type(ConversationType.TOOL)
                .model("qwen3:4b-instruct")
                .temperature(0.4)
                .think(false)
                .archived(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(conversationRepository.findByOwnerUsernameAndTypeAndArchivedFalseOrderByUpdatedAtDesc(USERNAME, ConversationType.TOOL))
                .thenReturn(List.of(entity));

        List<ConversationSummaryDto> summaries = conversationHistoryService.listConversations(USERNAME, ConversationType.TOOL);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().getTitle()).isEqualTo("Tool thread");
        assertThat(summaries.getFirst().getType()).isEqualTo(ConversationType.TOOL);
    }

    @Test
    void shouldReturnConversationDetailWithMessages() {
        UUID conversationId = UUID.randomUUID();
        ConversationEntity conversation = ConversationEntity.builder()
                .id(conversationId)
                .title("Existing")
                .type(ConversationType.CHAT)
                .model("qwen3:0.6b")
                .systemPromptSnapshot("snap")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(conversationRepository.findByIdAndOwnerUsernameAndArchivedFalse(conversationId, USERNAME))
                .thenReturn(Optional.of(conversation));

        ConversationMessageEntity message = ConversationMessageEntity.builder()
                .conversation(conversation)
                .role("system")
                .content("snap")
                .position(0)
                .build();

        when(conversationMessageRepository.findByConversationOrderByPositionAsc(conversation))
                .thenReturn(List.of(message));

        ConversationDetailDto detail = conversationHistoryService.getConversation(USERNAME, conversationId);

        assertThat(detail.getSummary().getId()).isEqualTo(conversationId);
        assertThat(detail.getMessages()).hasSize(1);
        assertThat(detail.getMessages().getFirst().getContent()).isEqualTo("snap");
    }

    @Test
    void shouldRenameConversation() {
        UUID conversationId = UUID.randomUUID();
        ConversationEntity conversation = ConversationEntity.builder()
                .id(conversationId)
                .title("Old")
                .type(ConversationType.CHAT)
                .model("qwen3:0.6b")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(conversationRepository.findByIdAndOwnerUsernameAndArchivedFalse(conversationId, USERNAME))
                .thenReturn(Optional.of(conversation));
        when(conversationRepository.save(conversation)).thenAnswer(invocation -> {
            ConversationEntity saved = invocation.getArgument(0);
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        UpdateConversationRequestDto request = UpdateConversationRequestDto.builder()
                .title("Renamed")
                .build();

        ConversationSummaryDto summary = conversationHistoryService.updateConversation(USERNAME, conversationId, request);

        assertThat(summary.getTitle()).isEqualTo("Renamed");
    }

    @Test
    void shouldThrowWhenConversationMissing() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findByIdAndOwnerUsernameAndArchivedFalse(conversationId, USERNAME))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationHistoryService.getConversation(USERNAME, conversationId))
                .isInstanceOf(CustomException.class);
    }

    private UserEntity sampleUser() {
        return UserEntity.builder()
                .username(USERNAME)
                .email("alice@example.com")
                .password("secret")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
    }
}
