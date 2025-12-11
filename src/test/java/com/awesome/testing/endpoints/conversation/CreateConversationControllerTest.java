package com.awesome.testing.endpoints.conversation;

import com.awesome.testing.dto.conversation.ConversationDetailDto;
import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.dto.conversation.CreateConversationRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CreateConversationControllerTest extends AbstractConversationControllerTest {

    @Test
    void shouldCreateConversationWithDefaultsAndSystemMessage() {
        String token = createClientToken();

        CreateConversationRequestDto request = CreateConversationRequestDto.builder()
                .type(ConversationType.CHAT)
                .title("Release notes")
                .build();

        ResponseEntity<ConversationDetailDto> response = executePost(
                CONVERSATIONS_ENDPOINT,
                request,
                getHeadersWith(token),
                ConversationDetailDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ConversationDetailDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getSummary().getId()).isNotNull();
        assertThat(body.getSummary().getTitle()).isEqualTo("Release notes");
        assertThat(body.getMessages()).hasSize(1);
        assertThat(body.getMessages().getFirst().getRole()).isEqualTo("system");
    }
}
