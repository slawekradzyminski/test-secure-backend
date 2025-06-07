# Tokenizer Implementation Plan

Below is a detailed implementation plan for replacing the current Java "tokenizer" functionality with calls to your newly created /tokenize endpoint on the Python sidecar.

## Overview
The plan assumes:
- Existing Java code uses Spring Boot 
- Uses WebClient-based "SidecarService" for Python sidecar communication
- Currently uses TokenizerController with local jtokkit

## 1. Create New Request and Response DTOs in Java

The Python sidecar expects a request body:

```json
{
  "text": "Hello world!",
  "model_name": "gpt2"
}
```

And responds with:

```json
{
  "tokens": ["Hello", "world", "!"],
  "model_name": "gpt2"
}
```

### 1.1 Create TokenizeRequestDto

Create `src/main/java/com/awesome/testing/dto/tokenizer/TokenizeRequestDto.java`:

```java
package com.awesome.testing.dto.tokenizer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for /tokenize endpoint in Python sidecar")
public class TokenizeRequestDto {

    @NotBlank
    @Schema(description = "The input text to split into tokens", example = "Hello world!")
    private String text;

    @Schema(description = "The name of the model's tokenizer to use", example = "gpt2", defaultValue = "gpt2")
    private String modelName;
}
```

Important notes:
- Field names must match Python sidecar expectations
- Use `@JsonProperty("model_name")` if needed for JSON mapping
- Default value for modelName can be set

### 1.2 Create TokenizeResponseDto

Create `src/main/java/com/awesome/testing/dto/tokenizer/TokenizeResponseDto.java`:

```java
package com.awesome.testing.dto.tokenizer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response from /tokenize endpoint in Python sidecar")
public class TokenizeResponseDto {

    @Schema(description = "List of tokens derived from the input text")
    private List<String> tokens;

    @Schema(description = "Name of the model used", example = "gpt2")
    private String modelName;
}
```

## 2. Add SidecarService Method

Add to your existing SidecarService:

```java
public Mono<TokenizeResponseDto> tokenize(TokenizeRequestDto request) {
    return embeddingsWebClient.post()
            .uri("/tokenize")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(TokenizeResponseDto.class)
            .timeout(Duration.ofMinutes(2))
            .doOnError(error -> handleError(error, "tokenize"))
            .retryWhen(
                Retry.backoff(2, Duration.ofSeconds(1))
                     .filter(throwable -> !(throwable instanceof WebClientResponseException))
                     .doBeforeRetry(retrySignal -> log.warn("Retrying tokenize request after error: {}, attempt: {}",
                             retrySignal.failure().getMessage(), retrySignal.totalRetries() + 1))
            )
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("Returning error response for tokenize: {}", ex.getMessage());
                return Mono.error(new WebClientException("Sidecar error", ex.getStatusCode()));
            });
}
```

## 3. Update TokenizerController

Replace or update your TokenizerController:

```java
package com.awesome.testing.controller.tokenizer;

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
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/tokenizer")
@Tag(name = "tokenizer", description = "Endpoints for text tokenization")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TokenizerController {

    private final SidecarService sidecarService;

    @PostMapping
    @Operation(summary = "Tokenize text by calling Python sidecar at /tokenize")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful tokenization"),
        @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
        @ApiResponse(responseCode = "500", description = "Sidecar server error", content = @Content)
    })
    public Mono<TokenizeResponseDto> tokenize(@Valid @RequestBody TokenizeRequestDto requestDto) {
        return sidecarService.tokenize(requestDto);
    }
}
```

## 4. Add Integration Tests

Create `src/test/java/com/awesome/testing/endpoints/tokenizer/TokenizerControllerTest.java`:

```java
package com.awesome.testing.endpoints.tokenizer;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.tokenizer.TokenizeRequestDto;
import com.awesome.testing.dto.tokenizer.TokenizeResponseDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.dto.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenizerControllerTest extends DomainHelper {

    private static final String TOKENIZER_ENDPOINT = "/api/tokenizer";

    @Test
    void shouldTokenizeText() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);

        TokenizeRequestDto requestDto = TokenizeRequestDto.builder()
                .text("Hello from Java test")
                .modelName("gpt2")
                .build();

        // when
        ResponseEntity<TokenizeResponseDto> response = executePost(
                TOKENIZER_ENDPOINT,
                requestDto,
                getHeadersWith(authToken),
                TokenizeResponseDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenizeResponseDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTokens()).isNotEmpty();
        assertThat(body.getModelName()).isEqualTo("gpt2");
    }

    @Test
    void shouldReturn401IfNoAuth() {
        // given
        TokenizeRequestDto requestDto = TokenizeRequestDto.builder()
                .text("Hello world")
                .modelName("gpt2")
                .build();

        // when
        ResponseEntity<TokenizeResponseDto> response = executePost(
                TOKENIZER_ENDPOINT,
                requestDto,
                getJsonOnlyHeaders(),
                TokenizeResponseDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

## 5. Swagger Documentation

The endpoint will be automatically documented via annotations in the controller.

## 6. Cleanup

1. Remove old TokenizationService and tests if not needed
2. Remove jtokkit dependencies
3. Update documentation

## 7. Deployment

Ensure:
- Python sidecar URL is configured correctly
- Docker compose includes sidecar service
- Application properties have correct sidecar URL

## 8. Testing Flow

1. Client -> POST /api/tokenizer with JSON body
2. TokenizerController -> sidecarService.tokenize()
3. SidecarService -> Python sidecar POST /tokenize
4. Response flows back to client

## 9. Summary

This implementation:
- Creates proper DTOs
- Adds sidecar service method
- Updates controller
- Adds integration tests
- Maintains security
- Documents API
- Removes legacy code