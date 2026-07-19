package com.awesome.testing.dto.gpt2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Gpt2InspectorStatusDto {
    private boolean available;
    private String mode;
    private String message;
    private String modelLabel;
    private String modelRevision;
    private Integer layerCount;
    private Integer headCount;
    private Integer maxTokens;
}
