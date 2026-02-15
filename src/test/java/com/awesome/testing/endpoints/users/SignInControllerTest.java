package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.UserResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

class SignInControllerTest extends DomainHelper {

    private String validUsername;
    private String validPassword;

    private static final String LOGIN_FAILED = "Invalid username/password supplied";

    @BeforeEach
    void prepareUserForTest() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        validUsername = user.getUsername();
        validPassword = user.getPassword();
        registerUser(user);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldLoginUser() {
        // when
        ResponseEntity<LoginResponseDto> responseWithToken =
                attemptLogin(new LoginDto(validUsername, validPassword), LoginResponseDto.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseWithToken.getBody().getToken()).isNotBlank();
        assertThat(responseWithToken.getBody().getRefreshToken()).isNotBlank();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void loggingReturnsValidToken() {
        // given
        String token = attemptLogin(new LoginDto(validUsername, validPassword), LoginResponseDto.class)
                .getBody()
                .getToken();

        // when
        ResponseEntity<UserResponseDto> response =
                executeGet(getUserEndpoint(validUsername),
                        getHeadersWith(token),
                        UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(validUsername);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldReturn400IfUsernameOrPasswordTooShort() {
        // when
        ResponseEntity<Map<String, String>> response = executePost(
                LOGIN_ENDPOINT,
                new LoginDto("one", "two"),
                getJsonOnlyHeaders(),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("username")).contains("username length");
        assertThat(response.getBody().get("password")).contains("password length");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldReturn422OnWrongPassword() {
        // when
        ResponseEntity<ErrorDto> responseWithToken =
                attemptLogin(new LoginDto(validUsername, "wrong"), ErrorDto.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(responseWithToken.getBody().getMessage()).isEqualTo(LOGIN_FAILED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldReturn422OnWrongUsername() {
        // when
        ResponseEntity<ErrorDto> responseWithToken =
                attemptLogin(new LoginDto("wrong", validPassword), ErrorDto.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(responseWithToken.getBody().getMessage()).isEqualTo(LOGIN_FAILED);
    }

}
