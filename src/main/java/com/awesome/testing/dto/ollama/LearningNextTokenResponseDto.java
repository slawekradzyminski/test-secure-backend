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
public class LearningNextTokenResponseDto {
    private String source;
    private String modelLabel;
    private String prompt;
    private String generatedToken;
    private double capturedProbabilityMass;
    private boolean truncated;
    private List<LearningNextTokenCandidateDto> candidates;
}
