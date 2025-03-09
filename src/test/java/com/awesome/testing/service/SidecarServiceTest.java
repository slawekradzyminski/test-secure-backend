package com.awesome.testing.service;

import com.awesome.testing.dto.embeddings.SidecarRequestDto;
import com.awesome.testing.dto.embeddings.SidecarResponseDto;
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
import static org.mockito.Mockito.mock;
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
    void shouldHandleShortText() {
        // given
        SidecarRequestDto request = new SidecarRequestDto();
        request.setText("Hello World");
        request.setModelName("gpt2");
        request.setDimensionalityReduction(true);
        request.setReductionMethod("pca");
        request.setNComponents(2);

        SidecarResponseDto expectedResponse = createMockResponse(Arrays.asList("Hello", "World"));
        when(responseSpecMock.bodyToMono(SidecarResponseDto.class)).thenReturn(Mono.just(expectedResponse));

        // when
        Mono<SidecarResponseDto> result = sidecarService.processText(request);

        // then
        StepVerifier.create(result)
                .expectNext(expectedResponse)
                .verifyComplete();
    }

    @Test
    void shouldHandleLongText() {
        // given
        SidecarRequestDto request = new SidecarRequestDto();
        request.setText("Tokenizer\nLearn about language model tokenization\nOpenAI's large language models process text using tokens, which are common sequences of characters found in a set of text. The models learn to understand the statistical relationships between these tokens, and excel at producing the next token in a sequence of tokens. Learn more.\n\nYou can use the tool below to understand how a piece of text might be tokenized by a language model, and the total count of tokens in that piece of text.");
        request.setModelName("gpt2");
        request.setDimensionalityReduction(true);
        request.setReductionMethod("pca");
        request.setNComponents(2);

        // Create a response with many tokens to simulate a large response
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            tokens.add("token" + i);
        }
        SidecarResponseDto expectedResponse = createMockResponse(tokens);
        when(responseSpecMock.bodyToMono(SidecarResponseDto.class)).thenReturn(Mono.just(expectedResponse));

        // when
        Mono<SidecarResponseDto> result = sidecarService.processText(request);

        // then
        StepVerifier.create(result)
                .expectNext(expectedResponse)
                .verifyComplete();
    }

    private SidecarResponseDto createMockResponse(List<String> tokens) {
        SidecarResponseDto response = new SidecarResponseDto();
        response.setTokens(tokens);
        
        // Create embeddings
        List<List<Float>> embeddings = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            List<Float> embedding = Arrays.asList(0.1f, 0.2f, 0.3f);
            embeddings.add(embedding);
        }
        response.setEmbeddings(embeddings);
        
        // Create reduced embeddings
        List<List<Double>> reducedEmbeddings = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            List<Double> reducedEmbedding = Arrays.asList(0.5, 0.6);
            reducedEmbeddings.add(reducedEmbedding);
        }
        response.setReducedEmbeddings(reducedEmbeddings);
        
        response.setModelName("gpt2");
        
        return response;
    }
} 