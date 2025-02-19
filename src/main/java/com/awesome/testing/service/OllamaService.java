package com.awesome.testing.service;

import com.awesome.testing.dto.ollama.GenerateRequestDto;
import com.awesome.testing.dto.ollama.GenerateResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {
    private final WebClient ollamaWebClient;
    
    public Flux<GenerateResponseDto> generateText(GenerateRequestDto request) {
        log.debug("Generating text with model: {}, prompt: {}", request.model(), request.prompt());
        
        // Ensure stream is enabled
        GenerateRequestDto streamingRequest = new GenerateRequestDto(
            request.model(),
            request.prompt(),
            true, // Force streaming to be enabled
            request.options()
        );
        
        return ollamaWebClient.post()
            .uri("/api/generate")
            .bodyValue(streamingRequest)
            .retrieve()
            .bodyToFlux(GenerateResponseDto.class)
            .doOnNext(response -> {
                if (response.done()) {
                    log.debug("Generation completed in {} ns", response.totalDuration());
                }
            })
            .doOnError(error -> 
                log.error("Error generating text: {}", error.getMessage())
            );
    }
} 