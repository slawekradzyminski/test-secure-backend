package com.awesome.testing.dto.gpt2;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Gpt2EmbeddingSpaceRequestDto {

    @Size(min = 1, max = 80)
    private String query;

    @Min(0)
    @Max(50256)
    private Integer tokenId;

    @Min(6)
    @Max(24)
    @Builder.Default
    private int neighborCount = 14;
}
