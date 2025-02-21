package com.awesome.testing.service;

import com.awesome.testing.dto.ollama.GenerateRequestDto;
import com.awesome.testing.dto.ollama.GenerateResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {
    private final WebClient ollamaWebClient;
    
    public Flux<GenerateResponseDto> generateText(GenerateRequestDto request) {
        AtomicInteger requestCount = new AtomicInteger(1);
        log.info("Handling generateText with model: {}, prompt: {}", request.model(), request.prompt());
        
        GenerateRequestDto streamingRequest = new GenerateRequestDto(
            request.model(),
            request.prompt(),
            true,
            request.options()
        );
        
        return ollamaWebClient.post()
            .uri("/api/generate")
            .bodyValue(streamingRequest)
            .retrieve()
            .bodyToFlux(GenerateResponseDto.class)
            .doOnNext(response -> {
                log.info("Received response for request #{}: {}", requestCount.get(), response.response());
                if (response.done()) {
                    log.info("Generation completed for request #{} in {} seconds", requestCount.get(), response.totalDuration() / 1000000000);
                }
                requestCount.incrementAndGet();
            })
            .doOnError(error -> 
                log.error("Error generating text for request #{}: {}", requestCount.get(), error.getMessage())
            );
    }
} 