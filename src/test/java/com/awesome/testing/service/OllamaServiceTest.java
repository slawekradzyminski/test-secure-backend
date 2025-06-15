package com.awesome.testing.service;

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
        StreamedRequestDto request = new StreamedRequestDto("qwen3:0.6b", "test prompt", null);
        GenerateResponseDto response1 = new GenerateResponseDto("qwen3:0.6b", "2024-02-21", "Hello", false, null, 100L);
        GenerateResponseDto response2 = new GenerateResponseDto("qwen3:0.6b", "2024-02-21", "World", true, null, 200L);

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
        StreamedRequestDto request = new StreamedRequestDto("invalid-model", "test", null);

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
}
