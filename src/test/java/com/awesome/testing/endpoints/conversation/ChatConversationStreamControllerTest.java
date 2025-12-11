package com.awesome.testing.endpoints.conversation;

import com.awesome.testing.dto.conversation.ConversationChatRequestDto;
import com.awesome.testing.dto.conversation.ConversationDetailDto;
import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.endpoints.ollama.OllamaMock;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatConversationStreamControllerTest extends AbstractConversationStreamTest {

    @Test
    void shouldStreamAssistantReplyAndPersistMessages() {
        String token = createClientToken();
        ConversationDetailDto created = createConversation(token, ConversationType.CHAT);
        UUID conversationId = created.getSummary().getId();
        OllamaMock.stubSuccessfulChat();

        ConversationChatRequestDto request = ConversationChatRequestDto.builder()
                .content("How was the release?")
                .build();

        ResponseEntity<String> response = executePostForEventStream(
                CONVERSATIONS_ENDPOINT + "/" + conversationId + "/chat",
                request,
                getHeadersWith(token),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/event-stream;charset=UTF-8");
        assertThat(response.getBody()).contains("friend!");

        ConversationDetailDto detail = executeGet(
                CONVERSATIONS_ENDPOINT + "/" + conversationId,
                getHeadersWith(token),
                ConversationDetailDto.class
        ).getBody();

        assertThat(detail).isNotNull();
        List<ChatMessageDto> messages = detail.getMessages();
        assertThat(messages).hasSizeGreaterThanOrEqualTo(3);
        assertThat(messages.get(1).getRole()).isEqualTo("user");
        assertThat(messages.getLast().getRole()).isEqualTo("assistant");
        assertThat(messages.getLast().getContent()).contains("friend!");
    }
}
