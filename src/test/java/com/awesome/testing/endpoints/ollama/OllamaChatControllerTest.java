package com.awesome.testing.endpoints.ollama;

import com.awesome.testing.dto.ollama.ChatRequestDto;
import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ModelNotFoundDto;
import com.awesome.testing.dto.ollama.OllamaToolDefinitionDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.service.ollama.OllamaToolDefinitionCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.factory.ollama.OllamaRequestFactory.*;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ConstantConditions")
class OllamaChatControllerTest extends AbstractOllamaTest {
    private static final String OLLAMA_CHAT_ENDPOINT = "/api/ollama/chat";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OllamaToolDefinitionCatalog toolDefinitionCatalog;

    @Test
    void shouldStreamChatResponseWithValidToken() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);
        ChatRequestDto request = validChatRequest();
        OllamaMock.stubSuccessfulChat();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                String.class,
                OLLAMA_CHAT_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/event-stream;charset=UTF-8");
        assertThat(response.getBody()).containsAnyOf("Hi", "there", "friend");

        verify(postRequestedFor(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("qwen3:4b-instruct")))
                .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
                .withRequestBody(matchingJsonPath("$.messages[1].role", equalTo("user")))
                .withRequestBody(matchingJsonPath("$.messages[1].content", equalTo("Hello")))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true"))));
    }

    @Test
    void shouldGet400WhenChatRequestIsInvalid() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);
        ChatRequestDto request = invalidChatRequest();

        // when
        ResponseEntity<Map<String, String>> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                mapTypeReference(),
                OLLAMA_CHAT_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("model", "must not be blank")
                .containsEntry("messages", "At least one message is required");
    }

    @Test
    void shouldGet400WhenToolMessageIsMissingToolName() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);
        ChatRequestDto request = ChatRequestDto.builder()
                .model("qwen3:4b-instruct")
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("tool")
                                .content("{}")
                                .build()
                ))
                .build();

        // when
        ResponseEntity<Map<String, String>> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                mapTypeReference(),
                OLLAMA_CHAT_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().values())
                .contains("Tool messages must include tool_name");
    }

    @Test
    void shouldGet401WhenNoAuthorizationHeaderForChat() {
        // given
        ChatRequestDto request = validChatRequest();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getJsonOnlyHeaders(),
                String.class,
                OLLAMA_CHAT_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldGet404ForMissingModelInChat() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);
        ChatRequestDto request = validChatRequest();
        OllamaMock.stubModelNotFoundForChat();

        // when
        ResponseEntity<ModelNotFoundDto> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                ModelNotFoundDto.class,
                OLLAMA_CHAT_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getBody().getError()).isEqualTo("model 'qwen3:4b-instruct' not found");
    }

    @Test
    void shouldGet500WhenOllamaServerFailsForChat() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);
        ChatRequestDto request = validChatRequest();
        OllamaMock.stubServerErrorForChat();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                String.class,
                OLLAMA_CHAT_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Internal server error");
    }

    @Test
    void shouldPassThinkFlagInChatRequest() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);
        ChatRequestDto request = validChatRequestWithThink();
        OllamaMock.stubSuccessfulChat();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                String.class,
                OLLAMA_CHAT_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/event-stream;charset=UTF-8");

        verify(postRequestedFor(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("qwen3:4b-instruct")))
                .withRequestBody(matchingJsonPath("$.think", equalTo("true"))));
    }

    @Test
    void shouldReceiveThinkingContentInChatResponse() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);
        ChatRequestDto request = validChatRequestWithThink();
        OllamaMock.stubSuccessfulChatWithThinking();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                String.class,
                OLLAMA_CHAT_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/event-stream;charset=UTF-8");
        
        // Verify response contains both thinking and content
        String responseBody = response.getBody();
        assertThat(responseBody).contains("thinking");
        assertThat(responseBody).contains("I need to think");
        assertThat(responseBody).contains("Hi there!");

        verify(postRequestedFor(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("qwen3:4b-instruct")))
                .withRequestBody(matchingJsonPath("$.think", equalTo("true"))));
    }

    @Test
    void shouldStreamToolResultBeforeFinalAssistantReply() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);
        ChatRequestDto request = validToolChatRequest();
        OllamaMock.stubToolCallingChatScenario();
        ensureProductExists("iPhone 13 Pro");

        ResponseEntity<String> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                String.class,
                "/api/ollama/chat/tools"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/event-stream;charset=UTF-8");

        String responseBody = response.getBody();
        assertThat(responseBody).contains("\"role\":\"tool\"");
        assertThat(responseBody).contains("iPhone 13 Pro");
        assertThat(responseBody).contains("999.99");

        int toolIndex = responseBody.indexOf("\"role\":\"tool\"");
        int finalReplyIndex = responseBody.indexOf("999.99");
        assertThat(toolIndex).isGreaterThan(-1);
        assertThat(finalReplyIndex).isGreaterThan(-1);
        assertThat(toolIndex).isLessThan(finalReplyIndex);

        verify(postRequestedFor(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath("$.tools[0].function.name", equalTo("get_product_snapshot")))
                .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
                .withRequestBody(matchingJsonPath("$.messages[1].role", equalTo("system")))
                .withRequestBody(matchingJsonPath("$.messages[2].content", containing("iPhone 13 Pro"))));
    }

    @Test
    void shouldExposeToolDefinitionsThroughController() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);

        ResponseEntity<OllamaToolDefinitionDto[]> response = executeGet(
                "/api/ollama/chat/tools/definitions",
                getHeadersWith(authToken),
                OllamaToolDefinitionDto[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isEqualTo(toolDefinitionCatalog.getDefinitions().size());
        assertThat(Arrays.stream(response.getBody())
                .map(dto -> dto.getFunction().getName()))
                .containsExactlyInAnyOrder(
                        "get_product_snapshot",
                        "list_products"
                );
    }
    private void ensureProductExists(String name) {
        productRepository.findFirstByNameIgnoreCaseOrderByIdAsc(name)
                .orElseGet(() -> productRepository.save(ProductEntity.builder()
                        .name(name)
                        .description("Test product for tool calling")
                        .price(new BigDecimal("999.99"))
                        .stockQuantity(50)
                        .category("Testing")
                        .imageUrl("http://example.com/test.png")
                        .build()));
    }

}
