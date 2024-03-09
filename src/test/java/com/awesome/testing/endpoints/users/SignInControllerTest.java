package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.*;
import com.awesome.testing.dto.users.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.testutil.TypeReferenceUtil.mapTypeReference;
import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class SignInControllerTest extends DomainHelper {

    private String validUsername;
    private String validPassword;

    private static final String LOGIN_FAILED = "Invalid username/password supplied";

    @BeforeEach
    public void prepareUserForTest() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        validUsername = user.getUsername();
        validPassword = user.getPassword();
        registerAndThenLoginSavingToken(user);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldLoginUser() {
        // when
        ResponseEntity<LoginResponseDto> responseWithToken =
                attemptLogin(new LoginDto(validUsername, validPassword), LoginResponseDto.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseWithToken.getBody().getToken()).isNotBlank();
        assertThat(responseWithToken.getHeaders().get("Set-Cookie").get(0)).contains("token=");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void loggingReturnsValidToken() {
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
    public void shouldReturn400IfUsernameOrPasswordTooShort() {
        // when
        ResponseEntity<Map<String, String>> response = restTemplate.exchange(
                LOGIN_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(new LoginDto("on", "wo"), getJsonOnlyHeaders()),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("username")).contains("username length");
        assertThat(response.getBody().get("password")).contains("password length");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldReturn422OnWrongPassword() {
        // when
        ResponseEntity<ErrorDto> responseWithToken =
                attemptLogin(new LoginDto(validUsername, "wrong"), ErrorDto.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(responseWithToken.getBody().getMessage()).isEqualTo(LOGIN_FAILED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldReturn422OnWrongUsername() {
        // when
        ResponseEntity<ErrorDto> responseWithToken =
                attemptLogin(new LoginDto("wrong", validPassword), ErrorDto.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(responseWithToken.getBody().getMessage()).isEqualTo(LOGIN_FAILED);
    }

}
