package com.awesome.testing.dto.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningTokenCountResponseDto {

    private String source;
    private String modelLabel;
    private String prompt;
    private int promptTokenCount;
    private String generatedToken;
}
