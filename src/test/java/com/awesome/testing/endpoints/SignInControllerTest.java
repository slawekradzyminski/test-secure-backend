package com.awesome.testing.endpoints;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.dto.ErrorDTO;
import com.awesome.testing.dto.LoginDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

public class SignInControllerTest extends HttpHelper {

    private static final String VALID_USERNAME = "admin";
    private static final String VALID_PASSWORD = "admin";

    private static final String LOGIN_FAILED = "Invalid username/password supplied";
    private static final String LOGIN_ENDPOINT = "/users/signin";

    @Test
    public void shouldLoginUser() {
        // when
        ResponseEntity<String> responseWithToken =
                attemptLogin(new LoginDto(VALID_USERNAME, VALID_PASSWORD), String.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseWithToken.getBody()).isNotBlank();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldReturn400IfUsernameOrPasswordTooShort() {
        // when
        ResponseEntity<Map<String, String>> response =  restTemplate.exchange(
                LOGIN_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(new LoginDto("one", "two"), getJsonOnlyHeaders()),
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
                attemptLogin(new LoginDto(VALID_USERNAME, "wrong"), ErrorDTO.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(responseWithToken.getBody().getMessage()).isEqualTo(LOGIN_FAILED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldReturn422OnWrongUsername() {
        // when
        ResponseEntity<ErrorDTO> responseWithToken =
                attemptLogin(new LoginDto("wrong", VALID_PASSWORD), ErrorDTO.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(responseWithToken.getBody().getMessage()).isEqualTo(LOGIN_FAILED);
    }

    private <T> ResponseEntity<T> attemptLogin(LoginDto loginDetails, Class<T> clazz) {
        return restTemplate.exchange(
                LOGIN_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(loginDetails, getRequiredHeaders()),
                clazz);
    }

    private HttpHeaders getRequiredHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "application/json");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }
}
