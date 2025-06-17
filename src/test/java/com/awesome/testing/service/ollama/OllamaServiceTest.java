package com.awesome.testing.service.ollama;

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

import static com.awesome.testing.factory.ollama.OllamaRequestFactory.*;
import static com.awesome.testing.factory.ollama.OllamaResponseFactory.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
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
        StreamedRequestDto request = validStreamedRequest();
        GenerateResponseDto response1 = simpleGenerateResponse();
        GenerateResponseDto response2 = finalGenerateResponse();

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
        StreamedRequestDto request = invalidStreamedRequest();

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
        ChatRequestDto request = validChatRequest();
        ChatResponseDto response1 = simpleChatResponse();
        ChatResponseDto response2 = finalChatResponse();

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
        ChatRequestDto request = invalidChatRequest();

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
        StreamedRequestDto request = validStreamedRequestWithThink();
        GenerateResponseDto response = completedGenerateResponse();

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
        ChatRequestDto request = validChatRequestWithThink();
        ChatResponseDto response = completedChatResponse();

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
        ChatRequestDto request = validChatRequestWithThink();
        ChatResponseDto thinkingResponse = thinkingChatResponse();
        ChatResponseDto contentResponse = contentChatResponse();

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
        StreamedRequestDto request = validStreamedRequestWithThink();
        GenerateResponseDto thinkingResponse = thinkingGenerateResponse();
        GenerateResponseDto contentResponse = contentGenerateResponse();

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
