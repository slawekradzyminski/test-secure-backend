package com.awesome.testing.service.gpt2;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.gpt2.Gpt2InspectorStatusDto;
import com.awesome.testing.dto.gpt2.Gpt2EmbeddingSpaceRequestDto;
import com.awesome.testing.dto.gpt2.Gpt2TraceRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class Gpt2InspectorService {

    private static final String FULL_LOCAL_MESSAGE =
            "Real GPT-2 activations are available only in the full local profile";
    private static final int INSPECTOR_RESPONSE_LIMIT_BYTES = 32 * 1024 * 1024;
    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration INSPECTION_TIMEOUT = Duration.ofMinutes(2);

    @Value("${gpt2-inspector.base-url:}")
    private String baseUrl;

    public Gpt2InspectorStatusDto status() {
        if (!isConfigured()) {
            return unavailable(FULL_LOCAL_MESSAGE);
        }

        try {
            JsonNode health = client().get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(STATUS_TIMEOUT)
                    .block();
            if (health == null || !"ready".equals(health.path("status").asString())) {
                return unavailable("The full-local GPT-2 inspector is still loading");
            }
            return Gpt2InspectorStatusDto.builder()
                    .available(true)
                    .mode("full-local")
                    .message("Real GPT-2 activations are ready")
                    .modelLabel(health.path("modelLabel").asString("openai-community/gpt2"))
                    .modelRevision(health.path("modelRevision").asString())
                    .layerCount(health.path("layerCount").asInt(12))
                    .headCount(health.path("headCount").asInt(12))
                    .maxTokens(health.path("maxTokens").asInt(32))
                    .build();
        } catch (RuntimeException exception) {
            return unavailable("The full-local GPT-2 inspector is starting or unavailable");
        }
    }

    public JsonNode trace(Gpt2TraceRequestDto request) {
        return post("/trace", request, "trace");
    }

    public JsonNode embeddingSpace(Gpt2EmbeddingSpaceRequestDto request) {
        if ((request.getQuery() == null || request.getQuery().isBlank()) && request.getTokenId() == null) {
            throw new CustomException("Provide a query or tokenId", HttpStatus.BAD_REQUEST);
        }
        return post("/embedding-space", request, "embedding neighborhood");
    }

    public JsonNode embeddingForest() {
        if (!isConfigured()) {
            throw new CustomException(FULL_LOCAL_MESSAGE, HttpStatus.SERVICE_UNAVAILABLE);
        }

        try {
            JsonNode response = client().get()
                    .uri("/embedding-forest")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(INSPECTION_TIMEOUT)
                    .block();
            if (response == null) {
                throw new CustomException("GPT-2 inspector returned an empty embedding forest", HttpStatus.BAD_GATEWAY);
            }
            return response;
        } catch (CustomException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            throw inspectorFailure("embedding forest", exception);
        } catch (RuntimeException exception) {
            log.error("GPT-2 inspector embedding forest request failed", exception);
            throw new CustomException(
                    "The full-local GPT-2 inspector could not produce the embedding forest",
                    HttpStatus.BAD_GATEWAY,
                    exception
            );
        }
    }

    private JsonNode post(String path, Object request, String label) {
        if (!isConfigured()) {
            throw new CustomException(FULL_LOCAL_MESSAGE, HttpStatus.SERVICE_UNAVAILABLE);
        }

        try {
            JsonNode response = client().post()
                    .uri(path)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(INSPECTION_TIMEOUT)
                    .block();
            if (response == null) {
                throw new CustomException("GPT-2 inspector returned an empty " + label, HttpStatus.BAD_GATEWAY);
            }
            return response;
        } catch (CustomException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            throw inspectorFailure(label, exception);
        } catch (RuntimeException exception) {
            log.error("GPT-2 inspector {} request failed", label, exception);
            throw new CustomException(
                    "The full-local GPT-2 inspector could not produce the " + label,
                    HttpStatus.BAD_GATEWAY,
                    exception
            );
        }
    }

    private boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    private static CustomException inspectorFailure(String label, WebClientResponseException exception) {
        if (exception.getStatusCode().is4xxClientError()) {
            return new CustomException(
                    "The full-local GPT-2 inspector rejected the " + label + " request",
                    HttpStatus.BAD_REQUEST,
                    exception
            );
        }
        return new CustomException(
                "The full-local GPT-2 inspector could not produce the " + label,
                HttpStatus.BAD_GATEWAY,
                exception
        );
    }

    private WebClient client() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(INSPECTOR_RESPONSE_LIMIT_BYTES))
                .build();
    }

    private static Gpt2InspectorStatusDto unavailable(String message) {
        return Gpt2InspectorStatusDto.builder()
                .available(false)
                .mode("full-local")
                .message(message)
                .build();
    }
}
