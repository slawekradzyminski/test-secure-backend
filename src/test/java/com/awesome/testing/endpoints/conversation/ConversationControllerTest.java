package com.awesome.testing.endpoints.conversation;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.conversation.ConversationDetailDto;
import com.awesome.testing.dto.conversation.ConversationSummaryDto;
import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.dto.conversation.CreateConversationRequestDto;
import com.awesome.testing.dto.conversation.UpdateConversationRequestDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.conversationSummaryListTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationControllerTest extends DomainHelper {

    private static final String CONVERSATIONS_ENDPOINT = "/api/ollama/conversations";

    @Test
    void shouldCreateListAndRetrieveConversation() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);
        assertThat(token).isNotBlank();

        CreateConversationRequestDto request = CreateConversationRequestDto.builder()
                .type(ConversationType.CHAT)
                .title("Release notes")
                .build();

        ResponseEntity<ConversationDetailDto> createResponse = executePost(
                CONVERSATIONS_ENDPOINT,
                request,
                getHeadersWith(token),
                ConversationDetailDto.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ConversationDetailDto created = createResponse.getBody();
        assertThat(created).isNotNull();
        UUID conversationId = created.getSummary().getId();
        assertThat(conversationId).isNotNull();
        assertThat(created.getMessages()).hasSize(1);
        assertThat(created.getMessages().getFirst().getRole()).isEqualTo("system");

        ResponseEntity<List<ConversationSummaryDto>> listResponse = executeGet(
                CONVERSATIONS_ENDPOINT,
                getHeadersWith(token),
                conversationSummaryListTypeReference()
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody().getFirst().getTitle()).isEqualTo("Release notes");

        ResponseEntity<ConversationDetailDto> detailResponse = executeGet(
                CONVERSATIONS_ENDPOINT + "/" + conversationId,
                getHeadersWith(token),
                ConversationDetailDto.class
        );

        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody()).isNotNull();
        assertThat(detailResponse.getBody().getMessages()).hasSize(1);
    }

    @Test
    void shouldRenameAndArchiveConversation() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);
        assertThat(token).isNotBlank();

        ConversationDetailDto created = executePost(
                CONVERSATIONS_ENDPOINT,
                CreateConversationRequestDto.builder()
                        .type(ConversationType.TOOL)
                        .build(),
                getHeadersWith(token),
                ConversationDetailDto.class
        ).getBody();

        assertThat(created).isNotNull();
        UUID conversationId = created.getSummary().getId();

        ResponseEntity<ConversationSummaryDto> updateResponse = executePatch(
                CONVERSATIONS_ENDPOINT + "/" + conversationId,
                UpdateConversationRequestDto.builder().title("Catalog follow-up").build(),
                getHeadersWith(token),
                ConversationSummaryDto.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).isNotNull();
        assertThat(updateResponse.getBody().getTitle()).isEqualTo("Catalog follow-up");

        ResponseEntity<Void> deleteResponse = executeDelete(
                CONVERSATIONS_ENDPOINT + "/" + conversationId,
                getHeadersWith(token),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List<ConversationSummaryDto>> listResponse = executeGet(
                CONVERSATIONS_ENDPOINT,
                getHeadersWith(token),
                conversationSummaryListTypeReference()
        );

        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody()).isEmpty();
    }
}
