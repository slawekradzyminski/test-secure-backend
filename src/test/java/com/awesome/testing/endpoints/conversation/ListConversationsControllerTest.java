package com.awesome.testing.endpoints.conversation;

import com.awesome.testing.dto.conversation.ConversationSummaryDto;
import com.awesome.testing.dto.conversation.ConversationType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.util.TypeReferenceUtil.conversationSummaryListTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

class ListConversationsControllerTest extends AbstractConversationControllerTest {

    @Test
    void shouldListConversationsFilteredByType() {
        String token = createClientToken();
        createConversation(token, ConversationType.TOOL);

        ResponseEntity<List<ConversationSummaryDto>> response = executeGet(
                CONVERSATIONS_ENDPOINT + "?type=TOOL",
                getHeadersWith(token),
                conversationSummaryListTypeReference()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().getFirst().getType()).isEqualTo(ConversationType.TOOL);
    }
}
