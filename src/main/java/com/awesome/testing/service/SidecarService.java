package com.awesome.testing.service;

import com.awesome.testing.controller.exception.WebClientException;
import com.awesome.testing.dto.embeddings.*;
import com.awesome.testing.dto.tokenizer.TokenizeRequestDto;
import com.awesome.testing.dto.tokenizer.TokenizeResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@RequiredArgsConstructor
@Service
@Slf4j
public class SidecarService {

    private final WebClient embeddingsWebClient;

    public Mono<TokenizeResponseDto> tokenize(TokenizeRequestDto request) {
        return sendRequest("/tokenize", request, TokenizeResponseDto.class, "tokenize");
    }

    public Mono<EmbeddingsResponseDto> getEmbeddings(EmbeddingsRequestDto request) {
        return sendRequest("/embeddings", request, EmbeddingsResponseDto.class, "embeddings");
    }

    public Mono<AttentionResponseDto> getAttention(AttentionRequestDto request) {
        return sendRequest("/attention", request, AttentionResponseDto.class, "attention");
    }

    public Mono<ReduceResponseDto> reduceEmbeddings(ReduceRequestDto request) {
        return sendRequest("/reduce", request, ReduceResponseDto.class, "reduce");
    }

    private <T, R> Mono<R> sendRequest(String uri, T request, Class<R> responseType, String logIdentifier) {
        return embeddingsWebClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofMinutes(2))
                .doOnError(error -> handleError(error, logIdentifier))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(throwable -> !(throwable instanceof WebClientResponseException))
                        .doBeforeRetry(retrySignal ->
                                log.warn("Retrying {} request after error: {}, attempt: {}",
                                        logIdentifier, retrySignal.failure().getMessage(), retrySignal.totalRetries() + 1)))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("Returning error response for {}: {}", logIdentifier, ex.getMessage());
                    return Mono.error(new WebClientException("Sidecar error", ex.getStatusCode()));
                });
    }

    private void handleError(Throwable error, String endpoint) {
        if (error instanceof WebClientResponseException ex) {
            log.error("Error from {} endpoint: Status: {}, Body: {}", endpoint, ex.getStatusCode(), ex.getResponseBodyAsString());
        } else {
            log.error("Error processing {} request: {}", endpoint, error.getMessage(), error);
        }
    }
}
