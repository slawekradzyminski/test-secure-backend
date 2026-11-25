package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.RefreshTokenRequestDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.TokenRefreshResponseDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class RefreshControllerTest extends DomainHelper {

    private static final String REFRESH_ENDPOINT = "/api/v1/users/refresh";

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldRefreshTokensUsingRefreshToken() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        LoginResponseDto loginResponse = registerAndLogin(user);

        // when
        TokenRefreshResponseDto response = executePost(
                REFRESH_ENDPOINT,
                new RefreshTokenRequestDto(loginResponse.getRefreshToken()),
                getJsonOnlyHeaders(),
                TokenRefreshResponseDto.class
        ).getBody();

        // then
        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotEqualTo(loginResponse.getRefreshToken());
    }

    @Test
    void shouldRejectUnknownRefreshToken() {
        // when
        ResponseEntity<ErrorDto> response = executePost(
                REFRESH_ENDPOINT,
                new RefreshTokenRequestDto("does-not-exist"),
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldRejectReuseOfRefreshToken() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        LoginResponseDto loginResponse = registerAndLogin(user);

        TokenRefreshResponseDto refreshed = executePost(
                REFRESH_ENDPOINT,
                new RefreshTokenRequestDto(loginResponse.getRefreshToken()),
                getJsonOnlyHeaders(),
                TokenRefreshResponseDto.class
        ).getBody();

        // when
        ResponseEntity<ErrorDto> response = executePost(
                REFRESH_ENDPOINT,
                new RefreshTokenRequestDto(loginResponse.getRefreshToken()),
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        // then
        assertThat(refreshed.getRefreshToken()).isNotEqualTo(loginResponse.getRefreshToken());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldRevokeWholeTokenFamilyWhenRotatedRefreshTokenIsReplayed() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        LoginResponseDto loginResponse = registerAndLogin(user);
        String firstRefreshToken = loginResponse.getRefreshToken();
        String secondRefreshToken = executePost(
                REFRESH_ENDPOINT,
                new RefreshTokenRequestDto(firstRefreshToken),
                getJsonOnlyHeaders(),
                TokenRefreshResponseDto.class
        ).getBody().getRefreshToken();

        // when
        ResponseEntity<ErrorDto> replay = executePost(
                REFRESH_ENDPOINT,
                new RefreshTokenRequestDto(firstRefreshToken),
                getJsonOnlyHeaders(),
                ErrorDto.class
        );
        ResponseEntity<ErrorDto> successorAfterReplay = executePost(
                REFRESH_ENDPOINT,
                new RefreshTokenRequestDto(secondRefreshToken),
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        // then
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replay.getBody().getMessage()).isEqualTo("Invalid refresh token");
        assertThat(successorAfterReplay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(successorAfterReplay.getBody().getMessage()).isEqualTo("Invalid refresh token");
    }

}
