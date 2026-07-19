package com.awesome.testing.controller;

import com.awesome.testing.dto.ollama.ChatRequestDto;
import com.awesome.testing.dto.ollama.ChatResponseDto;
import com.awesome.testing.dto.ollama.GenerateResponseDto;
import com.awesome.testing.dto.ollama.LearningEmbeddingRequestDto;
import com.awesome.testing.dto.ollama.LearningEmbeddingResponseDto;
import com.awesome.testing.dto.ollama.ModelNotFoundDto;
import com.awesome.testing.dto.ollama.LearningNextTokenRequestDto;
import com.awesome.testing.dto.ollama.LearningNextTokenResponseDto;
import com.awesome.testing.dto.ollama.LearningTokenCountRequestDto;
import com.awesome.testing.dto.ollama.LearningTokenCountResponseDto;
import com.awesome.testing.dto.ollama.OllamaToolDefinitionDto;
import com.awesome.testing.dto.ollama.StreamedRequestDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.ollama.OllamaFunctionCallingService;
import com.awesome.testing.service.ollama.OllamaLearningService;
import com.awesome.testing.service.ollama.OllamaService;
import com.awesome.testing.service.ollama.OllamaToolDefinitionCatalog;
import com.awesome.testing.service.prompt.PromptInjector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ollama")
@Tag(name = "ollama", description = "Ollama endpoints")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized")
public class OllamaController {

    private final OllamaService ollamaService;
    private final OllamaLearningService ollamaLearningService;
    private final OllamaFunctionCallingService functionCallingService;
    private final OllamaToolDefinitionCatalog toolDefinitionCatalog;
    private final PromptInjector promptInjector;
    private final AuthRateLimitGuard authRateLimitGuard;

    @Operation(summary = "Inspect live next-token probabilities",
            description = "Runs one raw Ollama generation step and returns a stable, ranked top-logprob teaching response.")
    @ApiResponse(responseCode = "200", description = "Next-token candidates returned successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "422", description = "The selected model or runtime did not expose logprobs")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    @PostMapping(value = "/learning/next-token", produces = MediaType.APPLICATION_JSON_VALUE)
    public LearningNextTokenResponseDto nextTokenDistribution(
            HttpServletRequest servletRequest,
            @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody LearningNextTokenRequestDto request) {
        authRateLimitGuard.checkOllama(servletRequest, principal != null ? principal.getUsername() : null);
        return ollamaLearningService.nextTokenDistribution(request).block();
    }

    @Operation(summary = "Verify a prompt token count with Ollama",
            description = "Runs one raw generation step and returns the number of prompt tokens processed by the model.")
    @ApiResponse(responseCode = "200", description = "Prompt token count returned successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "422", description = "The selected model or runtime did not expose a prompt token count")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    @PostMapping(value = "/learning/token-count", produces = MediaType.APPLICATION_JSON_VALUE)
    public LearningTokenCountResponseDto countPromptTokens(
            HttpServletRequest servletRequest,
            @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody LearningTokenCountRequestDto request) {
        authRateLimitGuard.checkOllama(servletRequest, principal != null ? principal.getUsername() : null);
        return ollamaLearningService.countPromptTokens(request).block();
    }

    @Operation(summary = "Generate text embeddings for the learning lab",
            description = "Embeds two to eight texts with a dedicated Ollama embedding model.")
    @ApiResponse(responseCode = "200", description = "Embedding vectors returned successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "422", description = "The selected model or runtime did not return valid embeddings")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    @PostMapping(value = "/learning/embeddings", produces = MediaType.APPLICATION_JSON_VALUE)
    public LearningEmbeddingResponseDto embedTexts(
            HttpServletRequest servletRequest,
            @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody LearningEmbeddingRequestDto request) {
        authRateLimitGuard.checkOllama(servletRequest, principal != null ? principal.getUsername() : null);
        return ollamaLearningService.embedTexts(request).block();
    }

    @Operation(summary = "Generate text using Ollama model",
            description = "Streams generated text chunks from the configured Ollama backend for a single prompt.")
    @ApiResponse(responseCode = "200", description = "Successful generation")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "404", description = "Model not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ModelNotFoundDto.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests")
    @ApiResponse(responseCode = "500", description = "Ollama server error")
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<GenerateResponseDto> generateText(HttpServletRequest servletRequest,
                                                  @AuthenticationPrincipal CustomPrincipal principal,
                                                  @Valid @RequestBody StreamedRequestDto request) {
        authRateLimitGuard.checkOllama(servletRequest, principal != null ? principal.getUsername() : null);
        return ollamaService.generateText(request)
                .doOnSubscribe(subscription -> log.info("Starting stream"));
    }

    @Operation(summary = "Chat with Ollama model (stateless endpoint)",
            description = "Streams responses and expects the caller to manage the entire conversation history client-side.")
    @ApiResponse(responseCode = "200", description = "Successful chat response")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "404", description = "Model not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ModelNotFoundDto.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests")
    @ApiResponse(responseCode = "500", description = "Ollama server error")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponseDto> chat(HttpServletRequest servletRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody ChatRequestDto request) {
        authRateLimitGuard.checkOllama(servletRequest, principal != null ? principal.getUsername() : null);
        log.info("Initiating chat request: model={}", request.getModel());
        ChatRequestDto augmented = promptInjector.augmentChatRequest(principal.getUsername(), request);
        return ollamaService.chat(augmented)
                .doOnSubscribe(subscription -> log.info("Starting chat stream"));
    }

    @Operation(
            summary = "Chat with Ollama using backend function calling (legacy stateless endpoint)",
            description = """
                    Available tools: get_product_snapshot, list_products.
                    Use GET /api/v1/ollama/chat/tools/definitions for the full JSON schema.
                    This endpoint requires the caller to resend the full conversation on every request.
                    Small local models stay grounded only when they keep everything in the product lane—always snapshot a SKU first and follow up with list_products if it needs comparisons."""
    )
    @ApiResponse(responseCode = "200", description = "Successful chat response")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    @ApiResponse(responseCode = "500", description = "Ollama server error")
    @PostMapping(value = "/chat/tools", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponseDto> chatWithTools(HttpServletRequest servletRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody ChatRequestDto request) {
        authRateLimitGuard.checkOllama(servletRequest, principal != null ? principal.getUsername() : null);
        int toolCount = request.getTools() != null ? request.getTools().size() : 0;
        log.info("Initiating tool-enabled chat: model={} tools={}", request.getModel(), toolCount);
        ChatRequestDto augmented = promptInjector.augmentToolRequest(principal.getUsername(), request);
        return functionCallingService.chatWithTools(augmented)
                .doOnSubscribe(subscription -> log.info("Starting tool-enabled chat stream"));
    }

    @Operation(summary = "List tool definitions supported by /api/v1/ollama/chat/tools",
            description = "Returns the backend function-calling tool schemas that clients can send to the tool-enabled chat endpoint.")
    @ApiResponse(responseCode = "200", description = "Available tool definitions returned successfully")
    @GetMapping("/chat/tools/definitions")
    public List<OllamaToolDefinitionDto> getToolDefinitions() {
        return toolDefinitionCatalog.getDefinitions();
    }
}
