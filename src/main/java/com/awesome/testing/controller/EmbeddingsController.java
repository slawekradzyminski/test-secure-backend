package com.awesome.testing.controller;

import com.awesome.testing.dto.embeddings.SidecarRequestDto;
import com.awesome.testing.dto.embeddings.SidecarResponseDto;
import com.awesome.testing.service.SidecarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/embeddings")
@Tag(name = "embeddings", description = "Embeddings & attention from Python sidecar")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Slf4j
public class EmbeddingsController {

    private final SidecarService sidecarService;

    @Operation(summary = "Process text to get tokens, embeddings, attention, optionally dimensionally reduced")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Python sidecar error or server error")
    })
    @PostMapping(value = "/process", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SidecarResponseDto> process(@Valid @RequestBody SidecarRequestDto requestDto) {
        log.info("Received request to process text of length: {}", requestDto.getText().length());

        Mono<SidecarResponseDto> sidecarResponseDtoMono = sidecarService.processText(requestDto)
                .timeout(Duration.ofSeconds(180))  // 3 minute timeout at controller level
                .doOnSuccess(response -> {
                    if (response != null) {
                        log.info("Successfully processed text with {} tokens",
                                response.getTokens() != null ? response.getTokens().size() : 0);
                    } else {
                        log.warn("Processed text but received null response");
                    }
                })
                .doOnError(error -> log.error("Error processing text: {}", error.getMessage()));
        return sidecarResponseDtoMono;
    }
} 