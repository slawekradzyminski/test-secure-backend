package com.awesome.testing.service.ollama;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.ollama.LearningNextTokenCandidateDto;
import com.awesome.testing.dto.ollama.LearningNextTokenRequestDto;
import com.awesome.testing.dto.ollama.LearningNextTokenResponseDto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OllamaLearningService {

    private static final String SOURCE = "ollama-live";
    private static final double COMPLETE_MASS_THRESHOLD = 0.999999d;

    private final WebClient ollamaWebClient;
    private final ObjectMapper objectMapper;

    public Mono<LearningNextTokenResponseDto> nextTokenDistribution(LearningNextTokenRequestDto request) {
        Map<String, Object> upstreamRequest = Map.of(
                "model", request.getModel(),
                "prompt", request.getPrompt(),
                "stream", false,
                "raw", true,
                "think", false,
                "logprobs", true,
                "top_logprobs", request.getTopK(),
                "options", Map.of(
                        "temperature", 1,
                        "num_predict", 1
                )
        );

        return ollamaWebClient.post()
                .uri("/api/generate")
                .bodyValue(upstreamRequest)
                .retrieve()
                .bodyToMono(String.class)
                .switchIfEmpty(Mono.error(unsupportedLogprobs()))
                .map(this::readResponse)
                .map(response -> parseResponse(request, response));
    }

    private JsonNode readResponse(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JacksonException exception) {
            throw new CustomException("Ollama returned an invalid JSON response", HttpStatus.BAD_GATEWAY, exception);
        }
    }

    LearningNextTokenResponseDto parseResponse(LearningNextTokenRequestDto request, JsonNode response) {
        JsonNode logprobs = response.path("logprobs");
        if (!logprobs.isArray() || logprobs.size() == 0) {
            throw unsupportedLogprobs();
        }

        JsonNode firstPosition = logprobs.get(0);
        String generatedToken = firstPosition.path("token").asString(response.path("response").asString(""));
        Map<String, Double> tokenLogprobs = new LinkedHashMap<>();

        JsonNode topLogprobs = firstPosition.path("top_logprobs");
        if (topLogprobs.isArray()) {
            topLogprobs.forEach(candidate -> addCandidate(tokenLogprobs, candidate));
        }
        addCandidate(tokenLogprobs, firstPosition);

        List<Map.Entry<String, Double>> validCandidates = tokenLogprobs.entrySet().stream()
                .filter(entry -> Double.isFinite(entry.getValue()))
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(request.getTopK())
                .toList();

        if (validCandidates.isEmpty()) {
            throw unsupportedLogprobs();
        }

        List<Double> rawProbabilities = validCandidates.stream()
                .map(entry -> Math.exp(entry.getValue()))
                .toList();
        double mass = rawProbabilities.stream()
                .filter(Double::isFinite)
                .mapToDouble(Double::doubleValue)
                .sum();
        if (!Double.isFinite(mass) || mass <= 0d) {
            throw unsupportedLogprobs();
        }

        List<LearningNextTokenCandidateDto> candidates = new ArrayList<>(validCandidates.size());
        for (int index = 0; index < validCandidates.size(); index++) {
            Map.Entry<String, Double> candidate = validCandidates.get(index);
            double probability = rawProbabilities.get(index);
            candidates.add(LearningNextTokenCandidateDto.builder()
                    .token(candidate.getKey())
                    .rank(index + 1)
                    .logprob(candidate.getValue())
                    .probability(probability)
                    .normalizedProbability(probability / mass)
                    .build());
        }

        double displayMass = Math.min(Math.max(mass, 0d), 1d);
        return LearningNextTokenResponseDto.builder()
                .source(SOURCE)
                .modelLabel(response.path("model").asString(request.getModel()))
                .prompt(request.getPrompt())
                .generatedToken(generatedToken)
                .capturedProbabilityMass(displayMass)
                .truncated(displayMass < COMPLETE_MASS_THRESHOLD)
                .candidates(candidates)
                .build();
    }

    private static void addCandidate(Map<String, Double> tokenLogprobs, JsonNode candidate) {
        JsonNode tokenNode = candidate.get("token");
        JsonNode logprobNode = candidate.get("logprob");
        if (tokenNode == null || !tokenNode.isString() || logprobNode == null || !logprobNode.isNumber()) {
            return;
        }

        double logprob = logprobNode.doubleValue();
        if (!Double.isFinite(logprob)) {
            return;
        }
        tokenLogprobs.merge(tokenNode.stringValue(), logprob, Math::max);
    }

    private static CustomException unsupportedLogprobs() {
        return new CustomException(
                "The selected model or Ollama runtime did not return next-token log probabilities",
                HttpStatus.UNPROCESSABLE_CONTENT
        );
    }
}
