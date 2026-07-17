package com.awesome.testing.dto.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningNextTokenCandidateDto {
    private String token;
    private int rank;
    private double logprob;
    private double probability;
    private double normalizedProbability;
}
