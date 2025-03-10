package com.awesome.testing.factory;

import com.awesome.testing.dto.embeddings.AttentionRequestDto;
import com.awesome.testing.dto.embeddings.EmbeddingsRequestDto;
import com.awesome.testing.dto.embeddings.ReduceRequestDto;
import net.datafaker.Faker;

public class EmbeddingsFactory {
    
    private static final Faker faker = new Faker();
    
    public static EmbeddingsRequestDto createValidEmbeddingsRequest() {
        return EmbeddingsRequestDto.builder()
                .text("Hello, world!")
                .modelName("gpt2")
                .build();
    }
    
    public static EmbeddingsRequestDto createInvalidEmbeddingsRequest() {
        return EmbeddingsRequestDto.builder()
                .text("")
                .modelName("gpt2")
                .build();
    }
    
    public static AttentionRequestDto createValidAttentionRequest() {
        return AttentionRequestDto.builder()
                .text("Hello, world!")
                .modelName("gpt2")
                .build();
    }
    
    public static AttentionRequestDto createInvalidAttentionRequest() {
        return AttentionRequestDto.builder()
                .text("")
                .modelName("gpt2")
                .build();
    }
    
    public static ReduceRequestDto createValidReduceRequest() {
        return ReduceRequestDto.builder()
                .text("Hello, world!")
                .modelName("gpt2")
                .reductionMethod("pca")
                .nComponents(2)
                .build();
    }
    
    public static ReduceRequestDto createInvalidReduceRequest() {
        return ReduceRequestDto.builder()
                .text("")
                .modelName("gpt2")
                .reductionMethod("pca")
                .nComponents(2)
                .build();
    }
    
    public static EmbeddingsRequestDto createRandomEmbeddingsRequest() {
        return EmbeddingsRequestDto.builder()
                .text(faker.lorem().paragraph())
                .modelName("gpt2")
                .build();
    }
    
    public static AttentionRequestDto createRandomAttentionRequest() {
        return AttentionRequestDto.builder()
                .text(faker.lorem().paragraph())
                .modelName("gpt2")
                .build();
    }
    
    public static ReduceRequestDto createRandomReduceRequest() {
        return ReduceRequestDto.builder()
                .text(faker.lorem().paragraph())
                .modelName("gpt2")
                .reductionMethod("pca")
                .nComponents(2)
                .build();
    }
} 