package com.awesome.testing.service;

import com.awesome.testing.dto.embeddings.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SidecarServiceTest {

    @Mock
    private WebClient webClientMock;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpecMock;

    @Mock
    private WebClient.RequestBodySpec requestBodySpecMock;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpecMock;

    @Mock
    private WebClient.ResponseSpec responseSpecMock;

    private SidecarService sidecarService;

    @BeforeEach
    void setUp() {
        sidecarService = new SidecarService(webClientMock);

        // Setup WebClient mock chain
        when(webClientMock.post()).thenReturn(requestBodyUriSpecMock);
        when(requestBodyUriSpecMock.uri(any(String.class))).thenReturn(requestBodySpecMock);
        when(requestBodySpecMock.contentType(any())).thenReturn(requestBodySpecMock);
        when(requestBodySpecMock.bodyValue(any())).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);
    }
    
    @Test
    void testGetEmbeddingsSuccessfully() {
        // given
        EmbeddingsRequestDto request = EmbeddingsRequestDto.builder()
                .text("Hello world")
                .modelName("gpt2")
                .build();
                
        EmbeddingsResponseDto mockResponse = EmbeddingsResponseDto.builder()
                .tokens(Arrays.asList("Hello", "world"))
                .embeddings(Arrays.asList(
                        Arrays.asList(0.1f, 0.2f, 0.3f),
                        Arrays.asList(0.4f, 0.5f, 0.6f)
                ))
                .modelName("gpt2")
                .build();

        when(responseSpecMock.bodyToMono(EmbeddingsResponseDto.class)).thenReturn(Mono.just(mockResponse));

        // when
        Mono<EmbeddingsResponseDto> responseMono = sidecarService.getEmbeddings(request);

        // then
        StepVerifier.create(responseMono)
                .expectNext(mockResponse)
                .verifyComplete();
    }
    
    @Test
    void testGetAttentionSuccessfully() {
        // given
        AttentionRequestDto request = AttentionRequestDto.builder()
                .text("Hello world")
                .modelName("gpt2")
                .build();
                
        List<List<List<List<Float>>>> attention = new ArrayList<>();
        List<List<List<Float>>> layer = new ArrayList<>();
        List<List<Float>> head = new ArrayList<>();
        head.add(Arrays.asList(0.9f, 0.1f));
        head.add(Arrays.asList(0.2f, 0.8f));
        layer.add(head);
        attention.add(layer);
        
        AttentionResponseDto mockResponse = AttentionResponseDto.builder()
                .tokens(Arrays.asList("Hello", "world"))
                .attention(attention)
                .modelName("gpt2")
                .build();

        when(responseSpecMock.bodyToMono(AttentionResponseDto.class)).thenReturn(Mono.just(mockResponse));

        // when
        Mono<AttentionResponseDto> responseMono = sidecarService.getAttention(request);

        // then
        StepVerifier.create(responseMono)
                .expectNext(mockResponse)
                .verifyComplete();
    }
    
    @Test
    void testReduceEmbeddingsSuccessfully() {
        // given
        ReduceRequestDto request = ReduceRequestDto.builder()
                .text("Hello world")
                .modelName("gpt2")
                .reductionMethod("pca")
                .nComponents(2)
                .build();
                
        ReduceResponseDto mockResponse = ReduceResponseDto.builder()
                .tokens(Arrays.asList("Hello", "world"))
                .reducedEmbeddings(Arrays.asList(
                        Arrays.asList(0.1, 0.2),
                        Arrays.asList(0.3, 0.4)
                ))
                .modelName("gpt2")
                .build();

        when(responseSpecMock.bodyToMono(ReduceResponseDto.class)).thenReturn(Mono.just(mockResponse));

        // when
        Mono<ReduceResponseDto> responseMono = sidecarService.reduceEmbeddings(request);

        // then
        StepVerifier.create(responseMono)
                .expectNext(mockResponse)
                .verifyComplete();
    }
} 