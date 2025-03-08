package com.awesome.testing.controller.tokenizer;

import com.awesome.testing.dto.tokenizer.TokenizeRequestDto;
import com.awesome.testing.dto.tokenizer.TokenizeResponseDto;
import com.awesome.testing.service.TokenizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tokenizer")
@Tag(name = "tokenizer", description = "Endpoints for text tokenization")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TokenizerController {

    private final TokenizationService tokenizationService;

    @PostMapping()
    @Operation(summary = "Tokenize text using")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful tokenization"),
        @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public TokenizeResponseDto tokenize(@Valid @RequestBody TokenizeRequestDto requestDto) {
        return tokenizationService.tokenize(requestDto);
    }
} 