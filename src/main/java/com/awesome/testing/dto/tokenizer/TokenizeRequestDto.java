package com.awesome.testing.dto.tokenizer;

import com.knuddels.jtokkit.api.ModelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenizeRequestDto {

    @Schema(description = "OpenAI model type to use for tokenization. Defaults to GPT4o", example = "GPT_4O")
    private ModelType modelType;

    @NotBlank
    @Schema(
            description = "The text to be tokenized",
            example = """
        OpenAI's large language models process text using tokens, which are common sequences of characters found in a set of text. The models learn to understand the statistical relationships between these tokens, and excel at producing the next token in a sequence of tokens. Learn more.

        You can use the tool below to understand how a piece of text might be tokenized by a language model, and the total count of tokens in that piece of text.
        """
    )
    private String text;
} 