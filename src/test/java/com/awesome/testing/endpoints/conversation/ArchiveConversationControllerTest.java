package com.awesome.testing.endpoints.conversation;

import com.awesome.testing.dto.conversation.ConversationDetailDto;
import com.awesome.testing.dto.conversation.ConversationSummaryDto;
import com.awesome.testing.dto.conversation.ConversationType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static com.awesome.testing.util.TypeReferenceUtil.conversationSummaryListTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

class ArchiveConversationControllerTest extends AbstractConversationControllerTest {

    @Test
    void shouldArchiveConversation() {
        String token = createClientToken();
        ConversationDetailDto created = createConversation(token, ConversationType.TOOL);
        UUID conversationId = created.getSummary().getId();

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
