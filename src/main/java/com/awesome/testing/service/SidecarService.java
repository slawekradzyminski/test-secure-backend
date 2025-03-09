package com.awesome.testing.service;

import com.awesome.testing.dto.embeddings.SidecarRequestDto;
import com.awesome.testing.dto.embeddings.SidecarResponseDto;
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

    public Mono<SidecarResponseDto> processText(SidecarRequestDto request) {
        log.debug("Processing text with length: {}, model: {}, dimensionality reduction: {}", 
                request.getText().length(), request.getModelName(), request.getDimensionalityReduction());
        
        return embeddingsWebClient.post()
                .uri("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SidecarResponseDto.class)
                .timeout(Duration.ofSeconds(120))  // Increase timeout to 2 minutes
                .doOnSubscribe(s -> log.debug("Sending request to sidecar service"))
                .doOnSuccess(response -> {
                    if (response != null) {
                        log.debug("Successfully received response from sidecar service. " +
                                "Tokens: {}, Embeddings size: {}, Reduced embeddings size: {}", 
                                response.getTokens() != null ? response.getTokens().size() : 0,
                                response.getEmbeddings() != null ? response.getEmbeddings().size() : 0,
                                response.getReducedEmbeddings() != null ? response.getReducedEmbeddings().size() : 0);
                    } else {
                        log.warn("Received null response from sidecar service");
                    }
                })
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException) {
                        WebClientResponseException ex = (WebClientResponseException) error;
                        log.error("Error from sidecar service: Status: {}, Body: {}", 
                                ex.getStatusCode(), ex.getResponseBodyAsString());
                    } else {
                        log.error("Error processing request: {}", error.getMessage(), error);
                    }
                })
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(throwable -> !(throwable instanceof WebClientResponseException))
                        .doBeforeRetry(retrySignal -> 
                            log.warn("Retrying request after error: {}, attempt: {}", 
                                    retrySignal.failure().getMessage(), 
                                    retrySignal.totalRetries() + 1)))
                .onErrorResume(error -> {
                    log.error("Failed to process text after retries: {}", error.getMessage());
                    return Mono.error(error);
                });
    }
} 