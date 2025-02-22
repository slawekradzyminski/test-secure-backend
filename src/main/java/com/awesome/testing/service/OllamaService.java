package com.awesome.testing.service;

import com.awesome.testing.dto.ollama.ChatRequestDto;
import com.awesome.testing.dto.ollama.ChatResponseDto;
import com.awesome.testing.dto.ollama.GenerateRequestDto;
import com.awesome.testing.dto.ollama.GenerateResponseDto;
import com.awesome.testing.dto.ollama.StreamedRequestDto;
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
    
    public Flux<GenerateResponseDto> generateText(StreamedRequestDto request) {
        AtomicInteger requestCount = new AtomicInteger(1);
        GenerateRequestDto streamingRequest = getStreamingRequest(request);
        log.info("Handling generateText with model: {}, prompt: {}", streamingRequest.getModel(), streamingRequest.getPrompt());

        return ollamaWebClient.post()
            .uri("/api/generate")
            .bodyValue(streamingRequest)
            .retrieve()
            .bodyToFlux(GenerateResponseDto.class)
            .doOnNext(response -> {
                log.info("Received response for request #{}: {}", requestCount.get(), response.getResponse());
                if (response.isDone()) {
                    log.info("Generation completed for request #{} in {} seconds", requestCount.get(), response.getTotalDuration() / 1000000000);
                }
                requestCount.incrementAndGet();
            })
            .doOnError(error -> 
                log.error("Error generating text for request #{}: {}", requestCount.get(), error.getMessage())
            );
    }

    private GenerateRequestDto getStreamingRequest(StreamedRequestDto request) {
        return new GenerateRequestDto(
            request.getModel(),
            request.getPrompt(),
            true,
            request.getOptions()
        );
    }

    public Flux<ChatResponseDto> chat(ChatRequestDto request) {
        boolean streamEnabled = (request.getStream() == null) ? true : request.getStream();
        log.info("Sending chat request to model: {}, streaming: {}", request.getModel(), streamEnabled);

        return ollamaWebClient.post()
            .uri("/api/chat")
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(ChatResponseDto.class)
            .doOnNext(chunk -> {
                log.debug("Received chunk: role={}, content={}",
                          chunk.getMessage().getRole(),
                          chunk.getMessage().getContent());
            })
            .doOnError(ex -> {
                log.error("Error during chat streaming: {}", ex.getMessage(), ex);
            })
            .doOnComplete(() -> {
                log.info("Chat streaming completed for model: {}", request.getModel());
            });
    }
} 