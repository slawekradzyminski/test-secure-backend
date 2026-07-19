package com.awesome.testing.dto.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningEmbeddingResponseDto {

    private String source;
    private String modelLabel;
    private int dimensions;
    private int promptTokenCount;
    private List<List<Double>> vectors;
}
