package com.awesome.testing.service;

import com.awesome.testing.dto.tokenizer.TokenDto;
import com.awesome.testing.dto.tokenizer.TokenizeRequestDto;
import com.awesome.testing.dto.tokenizer.TokenizeResponseDto;
import com.knuddels.jtokkit.api.ModelType;
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
    void shouldTokenizeAndCalculateStatsForValidText() {
        // given
        String helloWorld = "Hello, world!";
        TokenizeRequestDto dto = TokenizeRequestDto.builder()
                .modelType(ModelType.GPT_4O)
                .text(helloWorld)
                .build();

        // when
        TokenizeResponseDto response = service.tokenize(dto);

        // then
        assertThat(response.getTokenMap()).isNotEmpty();
        assertThat(response.getTokenCount()).isEqualTo(4);
        assertThat(response.getTokenMap()).containsExactly(
                TokenDto.builder().token("Hello").id(13225).build(),
                TokenDto.builder().token(",").id(11).build(),
                TokenDto.builder().token(" world").id(2375).build(),
                TokenDto.builder().token("!").id(0).build()
        );
        assertThat(response.getInputCharsCount()).isEqualTo(helloWorld.length());
        assertThat(response.getInputWordsCount()).isEqualTo(2);
        assertThat(response.getTokenToWordRatio()).isPositive();
    }

    @Test
    void shouldUseDefaultModelWhenModelTypeIsNull() {
        // given
        TokenizeRequestDto dto = TokenizeRequestDto.builder()
                .modelType(null)
                .text("Hello world!")
                .build();

        // when
        TokenizeResponseDto response = service.tokenize(dto);

        // then
        assertThat(response.getTokenMap()).isNotEmpty();
        assertThat(response.getTokenCount()).isGreaterThan(0);
    }

    @Test
    void shouldHandleEmptyTextGracefully() {
        // given
        TokenizeRequestDto dto = TokenizeRequestDto.builder()
                .text("   ") // only spaces
                .build();

        // when
        TokenizeResponseDto response = service.tokenize(dto);

        // then
        assertThat(response.getInputCharsCount()).isEqualTo(3);
        assertThat(response.getInputWordsCount()).isEqualTo(0);
        assertThat(response.getTokenToWordRatio()).isEqualTo(0.0);
    }
}