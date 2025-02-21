package com.awesome.testing.controller;

import com.awesome.testing.dto.ollama.GenerateRequestDto;
import com.awesome.testing.dto.ollama.GenerateResponseDto;
import com.awesome.testing.service.OllamaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/ollama")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class OllamaController {

    private final OllamaService ollamaService;

    @Operation(summary = "Generate text using Ollama model")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful generation"),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "422", description = "Invalid request", content = @Content),
        @ApiResponse(responseCode = "500", description = "Ollama server error", content = @Content)
    })
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<GenerateResponseDto> generateText(@Valid @RequestBody GenerateRequestDto request) {
        return ollamaService.generateText(request)
            .doOnSubscribe(subscription -> log.info("Starting stream"));
    }
} 