package com.awesome.testing.endpoints.tokenizer;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.tokenizer.TokenizeRequestDto;
import com.awesome.testing.dto.tokenizer.TokenizeResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class TokenizerControllerTest extends DomainHelper {

    private static final String API_TOKENIZER = "/api/tokenizer";

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldReturn200WithTokenStats() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);

        TokenizeRequestDto requestDto = TokenizeRequestDto.builder()
            .text("Hello, world!")
            .build();

        // when
        ResponseEntity<TokenizeResponseDto> response = executePost(
                API_TOKENIZER,
                requestDto,
                getHeadersWith(clientToken),
                TokenizeResponseDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTokenMap()).hasSize(4);
        assertThat(response.getBody().getTokenCount()).isEqualTo(4);
        assertThat(response.getBody().getInputCharsCount()).isEqualTo(13);
        assertThat(response.getBody().getInputWordsCount()).isEqualTo(2);
        assertThat(response.getBody().getTokenToWordRatio()).isGreaterThan(0.0);
    }

    @Test
    void shouldReturn400WhenTextIsBlank() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);

        TokenizeRequestDto requestDto = TokenizeRequestDto.builder()
            .text("")
            .build();

        // when
        ResponseEntity<Object> response = executePost(
                API_TOKENIZER,
                requestDto,
                getHeadersWith(clientToken),
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn401WhenNoTokenProvided() {
        // given
        TokenizeRequestDto requestDto = TokenizeRequestDto.builder()
            .text("Hello, world!")
            .build();

        // when
        ResponseEntity<Object> response = executePost(
                API_TOKENIZER,
                requestDto,
                null,
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

}