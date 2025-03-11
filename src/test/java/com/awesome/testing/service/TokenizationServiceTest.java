package com.awesome.testing.service;

import com.awesome.testing.dto.tokenizer.TokenizeRequestDto;
import com.awesome.testing.dto.tokenizer.TokenizeResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenizationServiceTest {

    private TokenizationService service;

    @BeforeEach
    void setUp() {
        service = new TokenizationService();
        service.initRegistry();
    }

    @Test
    void shouldTokenizeAndReturnTokens() {
        // given
        String helloWorld = "Hello, world!";
        TokenizeRequestDto dto = TokenizeRequestDto.builder()
                .text(helloWorld)
                .modelName("gpt2")
                .build();

        // when
        TokenizeResponseDto response = service.tokenize(dto);

        // then
        assertThat(response.getTokens()).isNotEmpty();
        assertThat(response.getTokens()).containsExactly("Hello", ",", "world", "!");
        assertThat(response.getModelName()).isEqualTo("gpt2");
    }

    @Test
    void shouldHandleEmptyTextGracefully() {
        // given
        TokenizeRequestDto dto = TokenizeRequestDto.builder()
                .text("   ") // only spaces
                .modelName("gpt2")
                .build();

        // when
        TokenizeResponseDto response = service.tokenize(dto);

        // then
        assertThat(response.getTokens()).isEmpty();
        assertThat(response.getModelName()).isEqualTo("gpt2");
    }
}