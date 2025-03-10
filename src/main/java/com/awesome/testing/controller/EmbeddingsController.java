package com.awesome.testing.controller;

import com.awesome.testing.controller.exception.WebClientException;
import com.awesome.testing.dto.embeddings.*;
import com.awesome.testing.service.SidecarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/embeddings")
@Tag(name = "embeddings", description = "Embeddings & attention from Python sidecar")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Slf4j
public class EmbeddingsController {

    private final SidecarService sidecarService;

    @Operation(summary = "Get token embeddings for the provided text")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Python sidecar error or server error")
    })
    @PostMapping(value = "/embeddings", produces = "application/json")
    public Mono<ResponseEntity<EmbeddingsResponseDto>> getEmbeddings(@Valid @RequestBody EmbeddingsRequestDto requestDto) {
        log.info("Received request to get embeddings for text of length: {}", requestDto.getText().length());
        return sidecarService.getEmbeddings(requestDto)
                .map(ResponseEntity::ok)
                .onErrorResume(WebClientException.class, ex -> {
                    log.error("Embeddings request failed: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.status(ex.getStatusCode()).build());
                });
    }

    @Operation(summary = "Get attention weights for the provided text")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Python sidecar error or server error")
    })
    @PostMapping(value = "/attention", produces = "application/json")
    public Mono<ResponseEntity<AttentionResponseDto>> getAttention(@Valid @RequestBody AttentionRequestDto requestDto) {
        log.info("Received request to get attention for text of length: {}", requestDto.getText().length());
        return sidecarService.getAttention(requestDto)
                .map(ResponseEntity::ok)
                .onErrorResume(WebClientException.class, ex -> {
                    log.error("Attention request failed: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.status(ex.getStatusCode()).build());
                });
    }

    @Operation(summary = "Get dimensionally reduced embeddings for the provided text")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Python sidecar error or server error")
    })
    @PostMapping(value = "/reduce", produces = "application/json")
    public Mono<ResponseEntity<ReduceResponseDto>> reduceEmbeddings(@Valid @RequestBody ReduceRequestDto requestDto) {
        log.info("Received request to reduce embeddings for text of length: {}", requestDto.getText().length());
        return sidecarService.reduceEmbeddings(requestDto)
                .map(ResponseEntity::ok)
                .onErrorResume(WebClientException.class, ex -> {
                    log.error("Reduce request failed: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.status(ex.getStatusCode()).build());
                });
    }
}
