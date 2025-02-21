package com.awesome.testing.service;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaServiceTest {
    @Mock
    private WebClient ollamaWebClient;
    
    @InjectMocks
    private OllamaService ollamaService;
    
    @Test
    void shouldStreamResponse() {
        // given
        StreamedRequestDto request = new StreamedRequestDto("gemma:2b", "test prompt", null);
        GenerateResponseDto response1 = new GenerateResponseDto("gemma:2b", "2024-02-21", "Hello", false, null, 100L);
        GenerateResponseDto response2 = new GenerateResponseDto("gemma:2b", "2024-02-21", "World", true, null, 200L);
        
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        
        // when
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/generate")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(GenerateRequestDto.class))).thenReturn(requestHeadersSpec);
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
        
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        
        // when
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/generate")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(GenerateRequestDto.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(GenerateResponseDto.class))
            .thenReturn(Flux.error(new RuntimeException("Model not found")));
        
        // then
        StepVerifier.create(ollamaService.generateText(request))
            .expectError(RuntimeException.class)
            .verify();
    }
} 