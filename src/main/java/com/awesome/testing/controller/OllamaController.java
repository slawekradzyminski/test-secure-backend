package com.awesome.testing.controller;

import com.awesome.testing.dto.ollama.GenerateRequestDto;
import com.awesome.testing.dto.ollama.GenerateResponseDto;
import com.awesome.testing.service.OllamaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/ollama")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class OllamaController {
    private final OllamaService ollamaService;
    private static final Logger log = LoggerFactory.getLogger(OllamaController.class);

    @Operation(summary = "Generate text using Ollama model")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful generation"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "422", description = "Invalid request"),
        @ApiResponse(responseCode = "500", description = "Ollama server error")
    })
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ROLE_CLIENT') or hasRole('ROLE_ADMIN')")
    public Flux<GenerateResponseDto> generateText(@Valid @RequestBody GenerateRequestDto request) {
        return ollamaService.generateText(request)
            .doOnSubscribe(subscription -> log.debug("Starting stream"))
            .doFinally(signalType -> {
                if (signalType == SignalType.ON_COMPLETE) {
                    log.debug("Stream completed successfully");
                } else {
                    log.debug("Stream ended with signal: {}", signalType);
                }
            });
    }
} 