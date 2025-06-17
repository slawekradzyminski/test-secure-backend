package com.awesome.testing.service.ollama;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ChatRequestDto;
import com.awesome.testing.dto.ollama.ChatResponseDto;
import com.awesome.testing.dto.ollama.GenerateRequestDto;
import com.awesome.testing.dto.ollama.GenerateResponseDto;
import com.awesome.testing.dto.ollama.StreamedRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaServiceTest {

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

    @InjectMocks
    private OllamaService ollamaService;

    @Test
    void shouldStreamResponse() {
        // given
        StreamedRequestDto request = StreamedRequestDto.builder()
                .model("qwen3:0.6b")
                .prompt("test prompt")
                .options(null)
                .build();
        GenerateResponseDto response1 = GenerateResponseDto.builder()
                .model("qwen3:0.6b")
                .createdAt("2024-02-21")
                .response("Hello")
                .thinking(null)
                .done(false)
                .context(null)
                .totalDuration(100L)
                .build();
        GenerateResponseDto response2 = GenerateResponseDto.builder()
                .model("qwen3:0.6b")
                .createdAt("2024-02-21")
                .response("World")
                .thinking(null)
                .done(true)
                .context(null)
                .totalDuration(200L)
                .build();

        // when
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/generate")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(GenerateRequestDto.class)))
                .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(GenerateResponseDto.class))
                .thenReturn(Flux.just(response1, response2));

        // then
        StepVerifier.create(ollamaService.generateText(request))
                .expectNext(response1)
                .expectNext(response2)
                .verifyComplete();
    }

    @Test
    void shouldHandleError() {
        // given
        StreamedRequestDto request = StreamedRequestDto.builder()
                .model("invalid-model")
                .prompt("test")
                .options(null)
                .build();

        // when
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/generate")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(GenerateRequestDto.class)))
                .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(GenerateResponseDto.class))
                .thenReturn(Flux.error(new RuntimeException("Model not found")));

        // then
        StepVerifier.create(ollamaService.generateText(request))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldStreamChatResponse() {
        // given
        ChatRequestDto request = ChatRequestDto.builder()
                .model("qwen3:0.6b")
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("user")
                                .content("Hello")
                                .build()
                ))
                .stream(true)
                .build();

        ChatResponseDto response1 = ChatResponseDto.builder()
                .model("qwen3:0.6b")
                .createdAt("2024-02-21")
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("Hi")
                        .build())
                .done(false)
                .build();

        ChatResponseDto response2 = ChatResponseDto.builder()
                .model("qwen3:0.6b")
                .createdAt("2024-02-21")
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("there!")
                        .build())
                .done(true)
                .build();

        // when
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/chat")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(ChatRequestDto.class)))
                .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(ChatResponseDto.class))
                .thenReturn(Flux.just(response1, response2));

        // then
        StepVerifier.create(ollamaService.chat(request))
                .expectNext(response1)
                .expectNext(response2)
                .verifyComplete();
    }

    @Test
    void shouldHandleChatError() {
        // given
        ChatRequestDto request = ChatRequestDto.builder()
                .model("invalid-model")
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("user")
                                .content("Hello")
                                .build()
                ))
                .build();

        // when
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/chat")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(ChatRequestDto.class)))
                .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(ChatResponseDto.class))
                .thenReturn(Flux.error(new RuntimeException("Model not found")));

        // then
        StepVerifier.create(ollamaService.chat(request))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldPassThinkFlagInGenerateRequest() {
        // given
        StreamedRequestDto request = StreamedRequestDto.builder()
                .model("qwen3:0.6b")
                .prompt("test prompt")
                .options(null)
                .think(true)
                .build();
        GenerateResponseDto response = GenerateResponseDto.builder()
                .model("qwen3:0.6b")
                .createdAt("2024-02-21")
                .response("Hello")
                .thinking(null)
                .done(true)
                .context(null)
                .totalDuration(100L)
                .build();

        // when
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/generate")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(GenerateRequestDto.class)))
                .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(GenerateResponseDto.class))
                .thenReturn(Flux.just(response));

        // then
        StepVerifier.create(ollamaService.generateText(request))
                .expectNext(response)
                .verifyComplete();
    }

    @Test
    void shouldPassThinkFlagInChatRequest() {
        // given
        ChatRequestDto request = ChatRequestDto.builder()
                .model("qwen3:0.6b")
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("user")
                                .content("Hello")
                                .build()
                ))
                .stream(true)
                .think(true)
                .build();

        ChatResponseDto response = ChatResponseDto.builder()
                .model("qwen3:0.6b")
                .createdAt("2024-02-21")
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("Hi")
                        .build())
                .done(true)
                .build();

        // when
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/chat")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(ChatRequestDto.class)))
                .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(ChatResponseDto.class))
                .thenReturn(Flux.just(response));

        // then
        StepVerifier.create(ollamaService.chat(request))
                .expectNext(response)
                .verifyComplete();
    }

    @Test
    void shouldHandleThinkingContentInChatResponse() {
        // given
        ChatRequestDto request = ChatRequestDto.builder()
                .model("qwen3:0.6b")
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("user")
                                .content("Complex question")
                                .build()
                ))
                .stream(true)
                .think(true)
                .build();

        ChatResponseDto thinkingResponse = ChatResponseDto.builder()
                .model("qwen3:0.6b")
                .createdAt("2024-02-21")
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("")
                        .thinking("Let me think about this...")
                        .build())
                .done(false)
                .build();

        ChatResponseDto contentResponse = ChatResponseDto.builder()
                .model("qwen3:0.6b")
                .createdAt("2024-02-21")
                .message(ChatMessageDto.builder()
                        .role("assistant")
                        .content("Here's my answer")
                        .thinking("")
                        .build())
                .done(true)
                .build();

        // when
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/chat")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(ChatRequestDto.class)))
                .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(ChatResponseDto.class))
                .thenReturn(Flux.just(thinkingResponse, contentResponse));

        // then
        StepVerifier.create(ollamaService.chat(request))
                .expectNext(thinkingResponse)
                .expectNext(contentResponse)
                .verifyComplete();
    }

    @Test
    void shouldHandleThinkingContentInGenerateResponse() {
        // given
        StreamedRequestDto request = StreamedRequestDto.builder()
                .model("qwen3:0.6b")
                .prompt("Complex question")
                .think(true)
                .build();

        GenerateResponseDto thinkingResponse = GenerateResponseDto.builder()
                .model("qwen3:0.6b")
                .createdAt("2024-02-21")
                .response("")
                .thinking("Let me think about this...")
                .done(false)
                .build();

        GenerateResponseDto contentResponse = GenerateResponseDto.builder()
                .model("qwen3:0.6b")
                .createdAt("2024-02-21")
                .response("Here's my answer")
                .thinking("")
                .done(true)
                .build();

        // when
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/generate")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(GenerateRequestDto.class)))
                .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(GenerateResponseDto.class))
                .thenReturn(Flux.just(thinkingResponse, contentResponse));

        // then
        StepVerifier.create(ollamaService.generateText(request))
                .expectNext(thinkingResponse)
                .expectNext(contentResponse)
                .verifyComplete();
    }
}
