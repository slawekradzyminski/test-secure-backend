package com.awesome.testing.controller.tokenizer;

import com.awesome.testing.controller.exception.WebClientException;
import com.awesome.testing.dto.tokenizer.TokenizeRequestDto;
import com.awesome.testing.dto.tokenizer.TokenizeResponseDto;
import com.awesome.testing.service.SidecarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
@RequestMapping("/api/tokenizer")
@Tag(name = "tokenizer", description = "Endpoints for text tokenization")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Slf4j
public class TokenizerController {

    private final SidecarService sidecarService;

    @Operation(summary = "Tokenize text by calling Python sidecar at /tokenize")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful tokenization"),
        @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "500", description = "Sidecar server error", content = @Content)
    })
    @PostMapping(produces = "application/json")
    public Mono<ResponseEntity<TokenizeResponseDto>> tokenize(@Valid @RequestBody TokenizeRequestDto requestDto) {
        log.info("Received request to tokenize text of length: {}", requestDto.getText().length());
        return sidecarService.tokenize(requestDto)
                .map(ResponseEntity::ok)
                .onErrorResume(WebClientException.class, ex -> {
                    log.error("Tokenize request failed: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.status(ex.getStatusCode()).build());
                });
    }
} 