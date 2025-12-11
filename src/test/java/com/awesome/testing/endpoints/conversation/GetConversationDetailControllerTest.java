package com.awesome.testing.endpoints.conversation;

import com.awesome.testing.dto.conversation.ConversationDetailDto;
import com.awesome.testing.dto.conversation.ConversationType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetConversationDetailControllerTest extends AbstractConversationControllerTest {

    @Test
    void shouldReturnConversationWithSystemMessage() {
        String token = createClientToken();
        ConversationDetailDto created = createConversation(token, ConversationType.CHAT);
        UUID conversationId = created.getSummary().getId();

        ResponseEntity<ConversationDetailDto> response = executeGet(
                CONVERSATIONS_ENDPOINT + "/" + conversationId,
                getHeadersWith(token),
                ConversationDetailDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ConversationDetailDto detail = response.getBody();
        assertThat(detail).isNotNull();
        assertThat(detail.getSummary().getId()).isEqualTo(conversationId);
        assertThat(detail.getMessages()).hasSize(1);
        assertThat(detail.getMessages().getFirst().getRole()).isEqualTo("system");
    }
}
