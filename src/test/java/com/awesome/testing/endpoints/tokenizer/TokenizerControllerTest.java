package com.awesome.testing.endpoints.tokenizer;

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

public class TokenizerControllerTest extends AbstractTokenizerTest {

    private static final String TOKENIZER_ENDPOINT = "/api/tokenizer";

    @Test
    void shouldTokenizeText() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);

        TokenizeRequestDto requestDto = TokenizeRequestDto.builder()
                .text("Hello from Java test")
                .modelName("gpt2")
                .build();

        stubTokenizeSuccess();

        // when
        ResponseEntity<TokenizeResponseDto> response = executePost(
                TOKENIZER_ENDPOINT,
                requestDto,
                getHeadersWith(authToken),
                TokenizeResponseDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenizeResponseDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTokens()).containsExactly("Hello", "from", "Java", "test");
        assertThat(body.getModelName()).isEqualTo("gpt2");
    }

    @Test
    void shouldReturn400WhenTextIsBlank() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);

        TokenizeRequestDto requestDto = TokenizeRequestDto.builder()
                .text("")
                .modelName("gpt2")
                .build();

        // when
        ResponseEntity<Object> response = executePost(
                TOKENIZER_ENDPOINT,
                requestDto,
                getHeadersWith(authToken),
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn401WhenNoAuth() {
        // given
        TokenizeRequestDto requestDto = TokenizeRequestDto.builder()
                .text("Hello world")
                .modelName("gpt2")
                .build();

        // when
        ResponseEntity<TokenizeResponseDto> response = executePost(
                TOKENIZER_ENDPOINT,
                requestDto,
                getJsonOnlyHeaders(),
                TokenizeResponseDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn500WhenSidecarFails() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String authToken = getToken(user);

        TokenizeRequestDto requestDto = TokenizeRequestDto.builder()
                .text("Hello world")
                .modelName("gpt2")
                .build();

        stubTokenizeServerError();

        // when
        ResponseEntity<Object> response = executePost(
                TOKENIZER_ENDPOINT,
                requestDto,
                getHeadersWith(authToken),
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}