package com.awesome.testing.endpoints.conversation;

import com.awesome.testing.dto.conversation.ConversationDetailDto;
import com.awesome.testing.dto.conversation.ConversationSummaryDto;
import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.dto.conversation.UpdateConversationRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateConversationControllerTest extends AbstractConversationControllerTest {

    @Test
    void shouldRenameConversation() {
        String token = createClientToken();
        ConversationDetailDto created = createConversation(token, ConversationType.CHAT);
        UUID conversationId = created.getSummary().getId();

        UpdateConversationRequestDto request = UpdateConversationRequestDto.builder()
                .title("Renamed conversation")
                .build();

        ResponseEntity<ConversationSummaryDto> response = executePatch(
                CONVERSATIONS_ENDPOINT + "/" + conversationId,
                request,
                getHeadersWith(token),
                ConversationSummaryDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ConversationSummaryDto summary = response.getBody();
        assertThat(summary).isNotNull();
        assertThat(summary.getTitle()).isEqualTo("Renamed conversation");
    }
}
