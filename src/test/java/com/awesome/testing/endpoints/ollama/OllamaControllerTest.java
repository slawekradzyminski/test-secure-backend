package com.awesome.testing.endpoints.ollama;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ollama.GenerateRequestDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.factory.ollama.OllamaRequestFactory.invalidGenerateRequest;
import static com.awesome.testing.factory.ollama.OllamaRequestFactory.validGenerateRequest;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ConstantConditions")
@AutoConfigureWireMock(port = 0)
class OllamaControllerTest extends DomainHelper {
    private static final String OLLAMA_ENDPOINT = "/api/ollama/generate";

    @Test
    void shouldStreamResponseWithValidToken() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);
        GenerateRequestDto request = validGenerateRequest();
        OllamaMock.stubSuccessfulGeneration();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/event-stream;charset=UTF-8");
        assertThat(response.getBody()).containsAnyOf("Hello", "world", "my friend");

        verify(postRequestedFor(urlEqualTo("/api/generate"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gemma:2b")))
                .withRequestBody(matchingJsonPath("$.prompt", equalTo("test prompt")))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true"))));
    }

    @Test
    void shouldGet400WhenRequestIsInvalid() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);
        GenerateRequestDto request = invalidGenerateRequest();

        // when
        ResponseEntity<Map<String, String>> response = executePostForEventStream(
                request,
                getHeadersWith(authToken),
                mapTypeReference()
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
        GenerateRequestDto request = invalidGenerateRequest();

        // when
        ResponseEntity<String> response = executePostForEventStream(
                request,
                getJsonOnlyHeaders(),
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private <T> ResponseEntity<T> executePostForEventStream(
            Object body, HttpHeaders headers, Class<T> responseType) {
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        return restTemplate.exchange(
                OLLAMA_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                responseType);
    }

    private <T> ResponseEntity<T> executePostForEventStream(
            Object body, HttpHeaders headers, ParameterizedTypeReference<T> responseType) {
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        return restTemplate.exchange(
                OLLAMA_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                responseType);
    }
}