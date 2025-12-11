package com.awesome.testing.endpoints.conversation;

import com.awesome.testing.dto.conversation.ConversationChatRequestDto;
import com.awesome.testing.dto.conversation.ConversationDetailDto;
import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.endpoints.ollama.OllamaMock;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ToolConversationStreamControllerTest extends AbstractConversationStreamTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldStreamToolAndAssistantMessages() {
        String token = createClientToken();
        ConversationDetailDto created = createConversation(token, ConversationType.TOOL);
        UUID conversationId = created.getSummary().getId();
        ensureProductExists("iPhone 13 Pro");
        OllamaMock.stubToolCallingChatScenario();

        ResponseEntity<String> response = executePostForEventStream(
                CONVERSATIONS_ENDPOINT + "/" + conversationId + "/chat/tools",
                ConversationChatRequestDto.builder()
                        .content("Price for iPhone 13 Pro?")
                        .build(),
                getHeadersWith(token),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"role\":\"tool\"");
        assertThat(response.getBody()).contains("999.99");

        ConversationDetailDto detail = executeGet(
                CONVERSATIONS_ENDPOINT + "/" + conversationId,
                getHeadersWith(token),
                ConversationDetailDto.class
        ).getBody();

        assertThat(detail).isNotNull();
        List<ChatMessageDto> messages = detail.getMessages();
        assertThat(messages.stream().anyMatch(m -> "tool".equals(m.getRole()))).isTrue();
        assertThat(messages.getLast().getRole()).isEqualTo("assistant");
        assertThat(messages.getLast().getContent()).contains("999.99");
    }

    private void ensureProductExists(String name) {
        productRepository.findFirstByNameIgnoreCaseOrderByIdAsc(name)
                .orElseGet(() -> productRepository.save(ProductEntity.builder()
                        .name(name)
                        .description("Test product")
                        .price(new BigDecimal("999.99"))
                        .stockQuantity(50)
                        .category("Testing")
                        .imageUrl("http://example.com/test.png")
                        .build()));
    }
}
