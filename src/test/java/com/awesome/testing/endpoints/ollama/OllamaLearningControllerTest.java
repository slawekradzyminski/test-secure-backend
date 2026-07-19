package com.awesome.testing.endpoints.ollama;

import com.awesome.testing.dto.ollama.LearningNextTokenRequestDto;
import com.awesome.testing.dto.ollama.LearningNextTokenResponseDto;
import com.awesome.testing.dto.ollama.LearningEmbeddingRequestDto;
import com.awesome.testing.dto.ollama.LearningEmbeddingResponseDto;
import com.awesome.testing.dto.ollama.LearningTokenCountRequestDto;
import com.awesome.testing.dto.ollama.LearningTokenCountResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class OllamaLearningControllerTest extends AbstractOllamaTest {

    private static final String ENDPOINT = "/api/v1/ollama/learning/next-token";
    private static final String TOKEN_COUNT_ENDPOINT = "/api/v1/ollama/learning/token-count";
    private static final String EMBEDDINGS_ENDPOINT = "/api/v1/ollama/learning/embeddings";
    private String authToken;

    @BeforeAll
    void initAuthToken() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        authToken = getToken(user);
    }

    @Test
    void returnsLiveDistributionAndSendsOneRawTokenRequest() {
        OllamaMock.stubSuccessfulNextTokenLogprobs();

        ResponseEntity<LearningNextTokenResponseDto> response = executePost(
                ENDPOINT, validRequest(), getHeadersWith(authToken), LearningNextTokenResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSource()).isEqualTo("ollama-live");
        assertThat(response.getBody().getCandidates()).hasSize(3);
        assertThat(response.getBody().getCandidates().getFirst().getToken()).isEqualTo(" Paris");

        verify(postRequestedFor(urlEqualTo("/api/generate"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("llama3.2:1b")))
                .withRequestBody(matchingJsonPath("$.prompt", equalTo("The capital of France is")))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.raw", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.logprobs", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.top_logprobs", equalTo("3")))
                .withRequestBody(matchingJsonPath("$.options.num_predict", equalTo("1"))));
    }

    @Test
    void rejectsInvalidRequestBeforeCallingOllama() {
        LearningNextTokenRequestDto request = LearningNextTokenRequestDto.builder()
                .model(" ")
                .prompt("")
                .topK(1)
                .build();

        ResponseEntity<Map> response = executePost(ENDPOINT, request, getHeadersWith(authToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKeys("model", "prompt", "topK");
    }

    @Test
    void requiresAuthentication() {
        ResponseEntity<String> response = executePost(ENDPOINT, validRequest(), getJsonOnlyHeaders(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requiresAuthenticationForTheNewLanguageFoundationEndpoints() {
        LearningTokenCountRequestDto tokenCountRequest = LearningTokenCountRequestDto.builder()
                .model("bonsai")
                .prompt("hello")
                .build();

        ResponseEntity<String> tokenCountResponse = executePost(
                TOKEN_COUNT_ENDPOINT, tokenCountRequest, getJsonOnlyHeaders(), String.class);
        ResponseEntity<String> embeddingsResponse = executePost(
                EMBEDDINGS_ENDPOINT, embeddingRequest(), getJsonOnlyHeaders(), String.class);

        assertThat(tokenCountResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(embeddingsResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reportsUnsupportedLogprobs() {
        OllamaMock.stubMissingNextTokenLogprobs();

        ResponseEntity<Map> response = executePost(ENDPOINT, validRequest(), getHeadersWith(authToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message").toString()).contains("did not return next-token log probabilities");
    }

    @Test
    void reportsAnEmptyUpstreamResponseAsUnsupportedLogprobs() {
        OllamaMock.stubEmptyNextTokenResponse();

        ResponseEntity<Map> response = executePost(ENDPOINT, validRequest(), getHeadersWith(authToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message").toString()).contains("did not return next-token log probabilities");
    }

    @Test
    void verifiesPromptTokenCountWithOneRawGenerationStep() {
        OllamaMock.stubSuccessfulTokenCount();
        LearningTokenCountRequestDto request = LearningTokenCountRequestDto.builder()
                .model("hf.co/prism-ml/Bonsai-27B-gguf:Q1_0")
                .prompt("Tokenization turns text into pieces.")
                .build();

        ResponseEntity<LearningTokenCountResponseDto> response = executePost(
                TOKEN_COUNT_ENDPOINT, request, getHeadersWith(authToken), LearningTokenCountResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPromptTokenCount()).isEqualTo(7);
        assertThat(response.getBody().getGeneratedToken()).isEqualTo(" pieces");
        verify(postRequestedFor(urlEqualTo("/api/generate"))
                .withRequestBody(matchingJsonPath("$.raw", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.logprobs", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.top_logprobs", equalTo("1")))
                .withRequestBody(matchingJsonPath("$.options.num_predict", equalTo("1"))));
    }

    @Test
    void reportsMissingPromptTokenCount() {
        OllamaMock.stubMissingTokenCount();
        LearningTokenCountRequestDto request = LearningTokenCountRequestDto.builder()
                .model("hf.co/prism-ml/Bonsai-27B-gguf:Q1_0")
                .prompt("Tokenization turns text into pieces.")
                .build();

        ResponseEntity<Map> response = executePost(
                TOKEN_COUNT_ENDPOINT, request, getHeadersWith(authToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message").toString()).contains("did not return a prompt token count");
    }

    @Test
    void returnsValidatedEmbeddingVectors() {
        OllamaMock.stubSuccessfulEmbeddings();
        LearningEmbeddingRequestDto request = embeddingRequest();

        ResponseEntity<LearningEmbeddingResponseDto> response = executePost(
                EMBEDDINGS_ENDPOINT, request, getHeadersWith(authToken), LearningEmbeddingResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getModelLabel()).isEqualTo("embeddinggemma");
        assertThat(response.getBody().getDimensions()).isEqualTo(4);
        assertThat(response.getBody().getPromptTokenCount()).isEqualTo(19);
        assertThat(response.getBody().getVectors()).hasSize(3).allSatisfy(vector -> assertThat(vector).hasSize(4));
        verify(postRequestedFor(urlEqualTo("/api/embed"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("embeddinggemma")))
                .withRequestBody(matchingJsonPath("$.input[0]", equalTo("A puppy is playing outside.")))
                .withRequestBody(matchingJsonPath("$.truncate", equalTo("false"))));
    }

    @Test
    void rejectsAnEmbeddingResponseThatDoesNotMatchTheInputCount() {
        OllamaMock.stubInvalidEmbeddings();

        ResponseEntity<Map> response = executePost(
                EMBEDDINGS_ENDPOINT, embeddingRequest(), getHeadersWith(authToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message").toString()).contains("did not return valid text embeddings");
    }

    @Test
    void rejectsTooMuchEmbeddingTextBeforeCallingOllama() {
        String longInput = "x".repeat(500);
        LearningEmbeddingRequestDto request = LearningEmbeddingRequestDto.builder()
                .model("embeddinggemma")
                .inputs(List.of(longInput, longInput, longInput, longInput, longInput))
                .build();

        ResponseEntity<Map> response = executePost(
                EMBEDDINGS_ENDPOINT, request, getHeadersWith(authToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").toString()).contains("at most 2000 characters in total");
    }

    private static LearningNextTokenRequestDto validRequest() {
        return LearningNextTokenRequestDto.builder()
                .model("llama3.2:1b")
                .prompt("The capital of France is")
                .topK(3)
                .build();
    }

    private static LearningEmbeddingRequestDto embeddingRequest() {
        return LearningEmbeddingRequestDto.builder()
                .model("embeddinggemma")
                .inputs(List.of(
                        "A puppy is playing outside.",
                        "A dog runs through the park.",
                        "The database migration failed."
                ))
                .build();
    }
}
