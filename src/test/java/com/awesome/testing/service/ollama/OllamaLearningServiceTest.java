package com.awesome.testing.service.ollama;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.ollama.LearningNextTokenRequestDto;
import com.awesome.testing.dto.ollama.LearningNextTokenResponseDto;
import com.awesome.testing.dto.ollama.LearningEmbeddingRequestDto;
import com.awesome.testing.dto.ollama.LearningEmbeddingResponseDto;
import com.awesome.testing.dto.ollama.LearningTokenCountRequestDto;
import com.awesome.testing.dto.ollama.LearningTokenCountResponseDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OllamaLearningServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OllamaLearningService service = new OllamaLearningService(mock(WebClient.class), objectMapper);

    @Test
    void parsesSortsDeduplicatesAndNormalizesModernLogprobs() throws Exception {
        LearningNextTokenRequestDto request = request(3);
        JsonNode response = objectMapper.readTree("""
                {
                  "model": "llama3.2:1b",
                  "response": " Paris",
                  "logprobs": [{
                    "token": " Paris",
                    "logprob": -0.3,
                    "top_logprobs": [
                      {"token": " Lyon", "logprob": -3.0},
                      {"token": " Paris", "logprob": -0.4},
                      {"token": " London", "logprob": -2.0},
                      {"token": " Berlin", "logprob": -4.0}
                    ]
                  }]
                }
                """);

        LearningNextTokenResponseDto result = service.parseResponse(request, response);

        assertThat(result.getSource()).isEqualTo("ollama-live");
        assertThat(result.getGeneratedToken()).isEqualTo(" Paris");
        assertThat(result.getCandidates()).extracting(candidate -> candidate.getToken())
                .containsExactly(" Paris", " London", " Lyon");
        assertThat(result.getCandidates()).extracting(candidate -> candidate.getRank())
                .containsExactly(1, 2, 3);
        assertThat(result.getCandidates().getFirst().getLogprob()).isEqualTo(-0.3d);
        assertThat(result.getCandidates()).allSatisfy(candidate -> {
            assertThat(candidate.getProbability()).isFinite().isPositive();
            assertThat(candidate.getNormalizedProbability()).isFinite().isPositive();
        });
        assertThat(result.getCandidates().stream().mapToDouble(candidate -> candidate.getNormalizedProbability()).sum())
                .isCloseTo(1d, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(result.isTruncated()).isTrue();
    }

    @Test
    void usesGeneratedPositionWhenTopCandidatesAreAbsent() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"response":"!","logprobs":[{"token":"!","logprob":0.0}]}
                """);

        LearningNextTokenResponseDto result = service.parseResponse(request(10), response);

        assertThat(result.getCandidates()).hasSize(1);
        assertThat(result.getCandidates().getFirst().getProbability()).isEqualTo(1d);
        assertThat(result.isTruncated()).isFalse();
    }

    @Test
    void rejectsMissingLogprobsWithUsefulStatus() throws Exception {
        JsonNode response = objectMapper.readTree("{\"response\":\" Paris\",\"logprobs\":null}");

        assertThatThrownBy(() -> service.parseResponse(request(10), response))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getMessage()).contains("did not return next-token log probabilities");
                });
    }

    @Test
    void parsesPromptTokenCountAndGeneratedEvidence() throws Exception {
        LearningTokenCountRequestDto request = LearningTokenCountRequestDto.builder()
                .model("bonsai")
                .prompt("A short prompt")
                .build();
        JsonNode response = objectMapper.readTree("""
                {"model":"bonsai:latest","response":" continues","prompt_eval_count":4}
                """);

        LearningTokenCountResponseDto result = service.parseTokenCountResponse(request, response);

        assertThat(result.getModelLabel()).isEqualTo("bonsai:latest");
        assertThat(result.getPromptTokenCount()).isEqualTo(4);
        assertThat(result.getGeneratedToken()).isEqualTo(" continues");
    }

    @Test
    void parsesFiniteEqualLengthEmbeddingVectors() throws Exception {
        LearningEmbeddingRequestDto request = embeddingRequest();
        JsonNode response = objectMapper.readTree("""
                {"model":"embeddinggemma","embeddings":[[0.8,0.4],[0.7,0.5]],"prompt_eval_count":8}
                """);

        LearningEmbeddingResponseDto result = service.parseEmbeddingResponse(request, response);

        assertThat(result.getDimensions()).isEqualTo(2);
        assertThat(result.getPromptTokenCount()).isEqualTo(8);
        assertThat(result.getVectors()).containsExactly(List.of(0.8, 0.4), List.of(0.7, 0.5));
    }

    @Test
    void rejectsNonFiniteOrRaggedEmbeddingVectors() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"embeddings":[[0.8,0.4],[0.7]],"prompt_eval_count":8}
                """);

        assertThatThrownBy(() -> service.parseEmbeddingResponse(embeddingRequest(), response))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getMessage()).contains("did not return valid text embeddings");
                });
    }

    private static LearningNextTokenRequestDto request(int topK) {
        return LearningNextTokenRequestDto.builder()
                .model("llama3.2:1b")
                .prompt("The capital of France is")
                .topK(topK)
                .build();
    }

    private static LearningEmbeddingRequestDto embeddingRequest() {
        return LearningEmbeddingRequestDto.builder()
                .model("embeddinggemma")
                .inputs(List.of("one", "two"))
                .build();
    }
}
