package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.*;
import com.awesome.testing.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class SignInControllerTest extends DomainHelper {

    private String validUsername;
    private String validPassword;

    private static final String LOGIN_FAILED = "Invalid username/password supplied";

    @BeforeEach
    public void prepareUserForTest() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        validUsername = user.getUsername();
        validPassword = user.getPassword();
        getToken(user);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldLoginUser() {
        // when
        ResponseEntity<LoginResponseDTO> responseWithToken =
                attemptLogin(new LoginDTO(validUsername, validPassword), LoginResponseDTO.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseWithToken.getBody().getToken()).isNotBlank();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void loggingReturnsValidToken() {
        // given
        String token = attemptLogin(new LoginDTO(validUsername, validPassword), LoginResponseDTO.class)
                .getBody()
                .getToken();

        // when
        ResponseEntity<UserResponseDTO> response =
                executeGet(getUserEndpoint(validUsername),
                        getHeadersWith(token),
                        UserResponseDTO.class);

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
                new HttpEntity<>(new LoginDTO("one", "two"), getJsonOnlyHeaders()),
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
        ResponseEntity<ErrorDTO> responseWithToken =
                attemptLogin(new LoginDTO(validUsername, "wrong"), ErrorDTO.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(responseWithToken.getBody().getMessage()).isEqualTo(LOGIN_FAILED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldReturn422OnWrongUsername() {
        // when
        ResponseEntity<ErrorDTO> responseWithToken =
                attemptLogin(new LoginDTO("wrong", validPassword), ErrorDTO.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(responseWithToken.getBody().getMessage()).isEqualTo(LOGIN_FAILED);
    }

}
