package com.awesome.testing.endpoints.users;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.SsoExchangeRequestDto;
import com.awesome.testing.service.SsoLoginService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SsoExchangeControllerTest extends HttpHelper {

    private static final String EXCHANGE_ENDPOINT = "/api/v1/users/sso/exchange";

    @MockitoBean
    private SsoLoginService ssoLoginService;

    @Test
    void shouldExchangeOidcTokenForAppTokens() {
        when(ssoLoginService.exchange("id-token")).thenReturn(LoginResponseDto.builder()
                .token("app-jwt")
                .refreshToken("refresh-token")
                .username("sso-client")
                .email("sso-client@example.com")
                .firstName("Sso")
                .lastName("Client")
                .roles(List.of(Role.ROLE_CLIENT))
                .build());

        ResponseEntity<LoginResponseDto> response = executePost(
                EXCHANGE_ENDPOINT,
                new SsoExchangeRequestDto("id-token"),
                getJsonOnlyHeaders(),
                LoginResponseDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isEqualTo("app-jwt");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void shouldReturnValidationErrorWhenTokenBlank() {
        ResponseEntity<Map> response = executePost(
                EXCHANGE_ENDPOINT,
                new SsoExchangeRequestDto(""),
                getJsonOnlyHeaders(),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("idToken");
    }

    @Test
    void shouldAllowExchangeEndpointWithoutAppJwt() {
        when(ssoLoginService.exchange("id-token")).thenReturn(LoginResponseDto.builder()
                .token("app-jwt")
                .refreshToken("refresh-token")
                .username("sso-client")
                .email("sso-client@example.com")
                .roles(List.of(Role.ROLE_CLIENT))
                .build());

        ResponseEntity<LoginResponseDto> response = executePost(
                EXCHANGE_ENDPOINT,
                new SsoExchangeRequestDto("id-token"),
                getJsonOnlyHeaders(),
                LoginResponseDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldReturnUnauthorizedWhenSsoTokenInvalid() {
        when(ssoLoginService.exchange("bad-token"))
                .thenThrow(new CustomException("Invalid SSO token", HttpStatus.UNAUTHORIZED));

        ResponseEntity<ErrorDto> response = executePost(
                EXCHANGE_ENDPOINT,
                new SsoExchangeRequestDto("bad-token"),
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid SSO token");
    }

    @Test
    void shouldReturnConflictWhenSsoEmailBelongsToAnotherLoginMethod() {
        when(ssoLoginService.exchange("conflict-token"))
                .thenThrow(new CustomException(
                        "Email is already used by another login method",
                        HttpStatus.CONFLICT
                ));

        ResponseEntity<ErrorDto> response = executePost(
                EXCHANGE_ENDPOINT,
                new SsoExchangeRequestDto("conflict-token"),
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Email is already used by another login method");
    }

}
