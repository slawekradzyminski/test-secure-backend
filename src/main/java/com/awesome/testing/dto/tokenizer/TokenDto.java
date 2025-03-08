package com.awesome.testing.dto.tokenizer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenDto {

    @Schema(description = "token", example = "open")
    @NotNull
    private String token;

    @Schema(description = "token id", example = "6439")
    @NotNull
    private Integer id;

}
