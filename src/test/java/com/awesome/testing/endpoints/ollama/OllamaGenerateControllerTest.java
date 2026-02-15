package com.awesome.testing.endpoints.ollama;

import com.awesome.testing.dto.ollama.ModelNotFoundDto;
import com.awesome.testing.dto.ollama.StreamedRequestDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.factory.ollama.OllamaRequestFactory.*;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ConstantConditions")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class OllamaGenerateControllerTest extends AbstractOllamaTest {
    private static final String OLLAMA_GENERATE_ENDPOINT = "/api/ollama/generate";
    private String authToken;

    @BeforeAll
    void initAuthToken() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        authToken = getToken(user);
    }

    @Test
    void shouldStreamResponseWithValidToken() {
        // given
        StreamedRequestDto request = validStreamedRequest();
        OllamaMock.stubSuccessfulGeneration();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                String.class,
                OLLAMA_GENERATE_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/event-stream");
        assertThat(response.getBody()).containsAnyOf("Hello", "world", "my friend");

        verify(postRequestedFor(urlEqualTo("/api/generate"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("qwen3:4b-instruct")))
                .withRequestBody(matchingJsonPath("$.prompt", equalTo("test prompt")))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true"))));
    }

    @Test
    void shouldGet400WhenRequestIsInvalid() {
        // given
        StreamedRequestDto request = invalidStreamedRequest();

        // when
        ResponseEntity<Map<String, String>> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                mapTypeReference(),
                OLLAMA_GENERATE_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("model", "must not be blank")
                .containsEntry("prompt", "must not be blank");
    }

    @Test
    void shouldGet401WhenNoAuthorizationHeader() {
        // given
        StreamedRequestDto request = invalidStreamedRequest();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getJsonOnlyHeaders(),
                String.class,
                OLLAMA_GENERATE_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldGet404ForMissingModel() {
        // given
        StreamedRequestDto request = validStreamedRequest();
        OllamaMock.stubModelNotFound();

        // when
        ResponseEntity<ModelNotFoundDto> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                ModelNotFoundDto.class,
                OLLAMA_GENERATE_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/json");
        assertThat(response.getBody().getError()).isEqualTo("model 'qwen3:4b-instruct' not found");
    }

    @Test
    void shouldGet500WhenOllamaServerFails() {
        // given
        StreamedRequestDto request = validStreamedRequest();
        OllamaMock.stubServerError();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                String.class,
                OLLAMA_GENERATE_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Internal server error");
    }

    @Test
    void shouldPassThinkFlagInGenerateRequest() {
        // given
        StreamedRequestDto request = validStreamedRequestWithThink();
        OllamaMock.stubSuccessfulGeneration();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                String.class,
                OLLAMA_GENERATE_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/event-stream");

        verify(postRequestedFor(urlEqualTo("/api/generate"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("qwen3:4b-instruct")))
                .withRequestBody(matchingJsonPath("$.think", equalTo("true"))));
    }

    @Test
    void shouldReceiveThinkingContentInGenerateResponse() {
        // given
        StreamedRequestDto request = validStreamedRequestWithThink();
        OllamaMock.stubSuccessfulGenerationWithThinking();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                String.class,
                OLLAMA_GENERATE_ENDPOINT
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/event-stream");
        
        // Verify response contains both thinking and content
        String responseBody = response.getBody();
        assertThat(responseBody).contains("thinking");
        assertThat(responseBody).contains("Let me think...");
        assertThat(responseBody).contains("Hello");

        verify(postRequestedFor(urlEqualTo("/api/generate"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("qwen3:4b-instruct")))
                .withRequestBody(matchingJsonPath("$.think", equalTo("true"))));
    }

}
