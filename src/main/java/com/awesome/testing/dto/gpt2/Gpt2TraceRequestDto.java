package com.awesome.testing.dto.gpt2;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Gpt2TraceRequestDto {

    @NotBlank
    @Size(max = 800)
    private String prompt;

    @Min(0)
    @Max(11)
    private int layer;

    @Min(0)
    @Max(11)
    private int head;

    @Min(0)
    @Max(31)
    private Integer selectedTokenIndex;
}
