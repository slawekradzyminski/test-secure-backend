package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.LoginDto;
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

class LogoutControllerTest extends DomainHelper {

    private static final String LOGOUT_ENDPOINT = "/users/logout";
    private static final String REFRESH_ENDPOINT = "/users/refresh";

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldInvalidateRefreshTokenOnLogout() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        LoginResponseDto loginResponse = registerAndLogin(user);

        // when
        ResponseEntity<Void> logoutResponse = executePost(
                LOGOUT_ENDPOINT,
                null,
                getHeadersWith(loginResponse.getToken()),
                Void.class
        );

        // then
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ErrorDto> refreshResponse = executePost(
                REFRESH_ENDPOINT,
                new RefreshTokenRequestDto(loginResponse.getRefreshToken()),
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldAllowFreshLoginAfterLogout() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        LoginResponseDto loginResponse = registerAndLogin(user);

        executePost(
                LOGOUT_ENDPOINT,
                null,
                getHeadersWith(loginResponse.getToken()),
                Void.class
        );

        // when
        LoginResponseDto newLoginResponse = attemptLogin(
                new LoginDto(user.getUsername(), user.getPassword()),
                LoginResponseDto.class
        ).getBody();

        TokenRefreshResponseDto refreshed = executePost(
                REFRESH_ENDPOINT,
                new RefreshTokenRequestDto(newLoginResponse.getRefreshToken()),
                getJsonOnlyHeaders(),
                TokenRefreshResponseDto.class
        ).getBody();

        // then
        assertThat(refreshed.getRefreshToken()).isNotBlank();
        assertThat(refreshed.getToken()).isNotBlank();
    }
}
