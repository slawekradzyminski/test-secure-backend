package com.awesome.testing.dto.tokenizer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TokenizeResponseDto {

    @Schema(
            description = "Response containing tokenized text information.",
            example = """
                      [
                        {
                          "token": "Open",
                          "id": 6447
                        },
                        {
                          "token": "AI",
                          "id": 17527
                        },
                        {
                          "token": "'s",
                          "id": 885
                        },
                        {
                          "token": " large",
                          "id": 4410
                        },
                        {
                          "token": " language",
                          "id": 6439
                        },
                        {
                          "token": " models",
                          "id": 7015
                        },
                        {
                          "token": " process",
                          "id": 2273
                        },
                        {
                          "token": " text",
                          "id": 2201
                        },
                        {
                          "token": " using",
                          "id": 2360
                        },
                        {
                          "token": " tokens",
                          "id": 20290
                        },
                        {
                          "token": ".",
                          "id": 13
                        }
                      ]
                    """
    )
    private List<TokenDto> tokenMap;

    @Schema(description = "Number of tokens", example = "10")
    private int tokenCount;

    @Schema(description = "Number of characters in the input text", example = "56")
    private int inputCharsCount;

    @Schema(description = "Number of words in the input text", example = "8")
    private int inputWordsCount;

    @Schema(description = "Ratio of tokens to words", example = "1.25")
    private double tokenToWordRatio;
}
