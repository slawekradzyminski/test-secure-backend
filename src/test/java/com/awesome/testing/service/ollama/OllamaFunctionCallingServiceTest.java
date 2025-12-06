package com.awesome.testing.service.ollama;

import com.awesome.testing.dto.ollama.*;
import com.awesome.testing.service.ollama.function.OllamaToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
@ExtendWith(MockitoExtension.class)
class OllamaFunctionCallingServiceTest {

    private static final String MODEL = "qwen3:4b-instruct";
    private static final Duration CHUNK_DELAY = Duration.ofMillis(50);

    @Mock
    private WebClient ollamaWebClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private OllamaToolRegistry toolRegistry;

    private OllamaFunctionCallingService functionCallingService;

    @BeforeEach
    void setUp() {
        functionCallingService = new OllamaFunctionCallingService(ollamaWebClient, toolRegistry);
    }

    @Test
    @DisplayName("Should stream chunks incrementally without buffering - regression test for collectList().block() fix")
    void shouldStreamChunksIncrementallyWithoutBuffering() {
        ChatRequestDto request = createToolChatRequest();
        ChatResponseDto chunk1 = createChunk("Hello ", false);
        ChatResponseDto chunk2 = createChunk("world ", false);
        ChatResponseDto chunk3 = createChunk("!", true);

        Flux<ChatResponseDto> delayedFlux = Flux.just(chunk1, chunk2, chunk3)
                .delayElements(CHUNK_DELAY);

        setupWebClientMock(delayedFlux);

        AtomicInteger chunkCount = new AtomicInteger(0);
        AtomicLong firstChunkTime = new AtomicLong(0);
        AtomicLong lastChunkTime = new AtomicLong(0);
        long startTime = System.currentTimeMillis();

        StepVerifier.create(functionCallingService.chatWithTools(request)
                        .doOnNext(chunk -> {
                            long now = System.currentTimeMillis();
                            if (chunkCount.incrementAndGet() == 1) {
                                firstChunkTime.set(now - startTime);
                            }
                            lastChunkTime.set(now - startTime);
                        }))
                .expectNextCount(3)
                .verifyComplete();

        assertThat(chunkCount.get()).isEqualTo(3);
        long totalDuration = lastChunkTime.get() - firstChunkTime.get();
        assertThat(totalDuration)
                .as("Chunks should be spread over time (incremental streaming), not arrive all at once")
                .isGreaterThan(CHUNK_DELAY.toMillis());
    }

    @Test
    @DisplayName("Should emit each chunk as it arrives from Ollama - verifies no buffering")
    void shouldEmitEachChunkAsItArrives() {
        ChatRequestDto request = createToolChatRequest();
        ChatResponseDto chunk1 = createChunk("Token1 ", false);
        ChatResponseDto chunk2 = createChunk("Token2 ", false);
        ChatResponseDto chunk3 = createChunk("Token3", true);

        Flux<ChatResponseDto> delayedFlux = Flux.just(chunk1, chunk2, chunk3)
                .delayElements(CHUNK_DELAY);

        setupWebClientMock(delayedFlux);

        AtomicInteger receivedCount = new AtomicInteger(0);
        List<Long> arrivalTimes = new java.util.ArrayList<>();
        long startTime = System.currentTimeMillis();

        StepVerifier.create(functionCallingService.chatWithTools(request)
                        .doOnNext(chunk -> {
                            receivedCount.incrementAndGet();
                            arrivalTimes.add(System.currentTimeMillis() - startTime);
                        }))
                .expectNext(chunk1)
                .expectNext(chunk2)
                .expectNext(chunk3)
                .verifyComplete();

        assertThat(arrivalTimes).hasSize(3);
        for (int i = 1; i < arrivalTimes.size(); i++) {
            long gap = arrivalTimes.get(i) - arrivalTimes.get(i - 1);
            assertThat(gap)
                    .as("Gap between chunk %d and %d should be approximately %dms (incremental streaming)", i, i + 1, CHUNK_DELAY.toMillis())
                    .isGreaterThanOrEqualTo(CHUNK_DELAY.toMillis() - 20);
        }
    }

    @Test
    @DisplayName("Should handle tool call and continue streaming after tool response")
    void shouldHandleToolCallAndContinueStreaming() {
        ChatRequestDto request = createToolChatRequest();

        ChatResponseDto toolCallChunk = createToolCallChunk("get_product_snapshot", Map.of("name", "iPhone"));
        ChatResponseDto finalChunk = createChunk("The iPhone costs $999.", true);

        ChatMessageDto toolResponse = ChatMessageDto.builder()
                .role("tool")
                .toolName("get_product_snapshot")
                .content("{\"name\":\"iPhone\",\"price\":999}")
                .build();

        Flux<ChatResponseDto> firstCallFlux = Flux.just(toolCallChunk)
                .delayElements(CHUNK_DELAY);
        Flux<ChatResponseDto> secondCallFlux = Flux.just(finalChunk)
                .delayElements(CHUNK_DELAY);

        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/chat")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(ChatRequestDto.class)))
                .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(ChatResponseDto.class))
                .thenReturn(firstCallFlux)
                .thenReturn(secondCallFlux);

        when(toolRegistry.execute(any(ToolCallDto.class))).thenReturn(toolResponse);

        AtomicInteger chunkCount = new AtomicInteger(0);

        StepVerifier.create(functionCallingService.chatWithTools(request)
                        .doOnNext(chunk -> chunkCount.incrementAndGet()))
                .expectNextMatches(chunk -> chunk.getMessage() != null &&
                        chunk.getMessage().getToolCalls() != null &&
                        !chunk.getMessage().getToolCalls().isEmpty())
                .expectNextMatches(chunk -> "tool".equals(chunk.getMessage().getRole()))
                .expectNextMatches(chunk -> chunk.getMessage().getContent().contains("$999"))
                .verifyComplete();

        assertThat(chunkCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should fail when no tools are provided")
    void shouldFailWhenNoToolsProvided() {
        ChatRequestDto request = ChatRequestDto.builder()
                .model(MODEL)
                .messages(List.of(createUserMessage("Hello")))
                .tools(List.of())
                .stream(true)
                .build();

        StepVerifier.create(functionCallingService.chatWithTools(request))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("Should fail when tools are null")
    void shouldFailWhenToolsAreNull() {
        ChatRequestDto request = ChatRequestDto.builder()
                .model(MODEL)
                .messages(List.of(createUserMessage("Hello")))
                .tools(null)
                .stream(true)
                .build();

        StepVerifier.create(functionCallingService.chatWithTools(request))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("Should propagate errors from Ollama")
    void shouldPropagateErrorsFromOllama() {
        ChatRequestDto request = createToolChatRequest();

        Flux<ChatResponseDto> errorFlux = Flux.error(new RuntimeException("Ollama connection failed"));

        setupWebClientMock(errorFlux);

        StepVerifier.create(functionCallingService.chatWithTools(request))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("Should complete stream when Ollama returns empty response")
    void shouldCompleteStreamWhenOllamaReturnsEmptyResponse() {
        ChatRequestDto request = createToolChatRequest();

        Flux<ChatResponseDto> emptyFlux = Flux.empty();

        setupWebClientMock(emptyFlux);

        StepVerifier.create(functionCallingService.chatWithTools(request))
                .verifyComplete();
    }

    @Test
    @DisplayName("REGRESSION: Chunks must be emitted incrementally - would fail with collectList().block()")
    void regressionChunksMustBeEmittedIncrementally() {
        ChatRequestDto request = createToolChatRequest();
        ChatResponseDto chunk1 = createChunk("First ", false);
        ChatResponseDto chunk2 = createChunk("Second ", false);
        ChatResponseDto chunk3 = createChunk("Third", true);

        List<Long> emissionTimestamps = new ArrayList<>();

        Flux<ChatResponseDto> delayedFlux = Flux.just(chunk1, chunk2, chunk3)
                .delayElements(Duration.ofMillis(100));

        setupWebClientMock(delayedFlux);

        AtomicLong firstChunkReceivedTime = new AtomicLong(0);
        
        StepVerifier.create(functionCallingService.chatWithTools(request)
                        .doOnNext(chunk -> {
                            long now = System.currentTimeMillis();
                            if (firstChunkReceivedTime.get() == 0) {
                                firstChunkReceivedTime.set(now);
                            }
                            emissionTimestamps.add(now - firstChunkReceivedTime.get());
                        }))
                .expectNextCount(3)
                .verifyComplete();

        assertThat(emissionTimestamps).hasSize(3);
        
        long firstToLastSpread = emissionTimestamps.getLast() - emissionTimestamps.getFirst();
        
        assertThat(firstToLastSpread)
                .as("With proper streaming (blockLast), chunks should be spread over time (~200ms for 3 chunks with 100ms delay). " +
                    "With collectList().block(), all chunks would arrive at once (0ms spread) because " +
                    "collectList buffers everything before emitting.")
                .isGreaterThan(150);
    }

    @Test
    @DisplayName("REGRESSION: Verify incremental emission order matches source order")
    void regressionVerifyIncrementalEmissionOrderMatchesSourceOrder() {
        ChatRequestDto request = createToolChatRequest();
        ChatResponseDto chunk1 = createChunk("A", false);
        ChatResponseDto chunk2 = createChunk("B", false);
        ChatResponseDto chunk3 = createChunk("C", true);

        Flux<ChatResponseDto> delayedFlux = Flux.just(chunk1, chunk2, chunk3)
                .delayElements(Duration.ofMillis(30));

        setupWebClientMock(delayedFlux);

        List<String> receivedContent = new ArrayList<>();

        StepVerifier.create(functionCallingService.chatWithTools(request)
                        .doOnNext(chunk -> receivedContent.add(chunk.getMessage().getContent())))
                .expectNext(chunk1)
                .expectNext(chunk2)
                .expectNext(chunk3)
                .verifyComplete();

        assertThat(receivedContent)
                .as("Chunks should be received in the exact order they were emitted")
                .containsExactly("A", "B", "C");
    }

    private void setupWebClientMock(Flux<ChatResponseDto> responseFlux) {
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/chat")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(ChatRequestDto.class)))
                .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(ChatResponseDto.class)).thenReturn(responseFlux);
    }

    private ChatRequestDto createToolChatRequest() {
        OllamaToolDefinitionDto toolDefinition = OllamaToolDefinitionDto.builder()
                .function(OllamaToolFunctionDto.builder()
                        .name("get_product_snapshot")
                        .description("Return catalog metadata for a product")
                        .parameters(OllamaToolParametersDto.builder()
                                .type("object")
                                .properties(Map.of(
                                        "name", OllamaToolSchemaPropertyDto.builder()
                                                .type("string")
                                                .description("Product name")
                                                .build()
                                ))
                                .required(List.of("name"))
                                .build())
                        .build())
                .build();

        return ChatRequestDto.builder()
                .model(MODEL)
                .messages(List.of(createUserMessage("Tell me about the iPhone")))
                .tools(List.of(toolDefinition))
                .stream(true)
                .build();
    }

    private ChatMessageDto createUserMessage(String content) {
        return ChatMessageDto.builder()
                .role("user")
                .content(content)
                .build();
    }

    private ChatResponseDto createChunk(String content, boolean done) {
        return ChatResponseDto.builder()
                .model(MODEL)
                .createdAt("2025-02-21T14:28:24Z")
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content(content)
                        .build())
                .done(done)
                .build();
    }

    private ChatResponseDto createToolCallChunk(String functionName, Map<String, Object> arguments) {
        return ChatResponseDto.builder()
                .model(MODEL)
                .createdAt("2025-02-21T14:28:24Z")
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("")
                        .toolCalls(List.of(ToolCallDto.builder()
                                .function(ToolCallFunctionDto.builder()
                                        .name(functionName)
                                        .arguments(arguments)
                                        .build())
                                .build()))
                        .build())
                .done(true)
                .build();
    }
}

