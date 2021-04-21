package com.awesome.testing;

import com.awesome.testing.dto.ErrorDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.text.MessageFormat;

import static org.assertj.core.api.Assertions.assertThat;

public class SignInControllerTest extends HttpHelper {

    private static final String VALID_USERNAME = "admin";
    private static final String VALID_PASSWORD = "admin";

    private static final String LOGIN_FAILED = "Invalid username/password supplied";

    @Test
    public void shouldLoginUser() {
        // when
        ResponseEntity<String> responseWithToken =
                attemptLogin(VALID_USERNAME, VALID_PASSWORD, String.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseWithToken.getBody()).isNotBlank();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldReturn422OnWrongPassword() {
        // when
        ResponseEntity<ErrorDTO> responseWithToken =
                attemptLogin(VALID_USERNAME, "wrong", ErrorDTO.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(responseWithToken.getBody().getMessage()).isEqualTo(LOGIN_FAILED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldReturn422OnWrongUsername() {
        // when
        ResponseEntity<ErrorDTO> responseWithToken =
                attemptLogin("wrong", VALID_PASSWORD, ErrorDTO.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(responseWithToken.getBody().getMessage()).isEqualTo(LOGIN_FAILED);
    }

    private <T> ResponseEntity<T> attemptLogin(String username, String password, Class<T> clazz) {
        return restTemplate.exchange(
                MessageFormat.format("/users/signin?password={0}&username={1}", password, username),
                HttpMethod.POST,
                new HttpEntity<>("", getRequiredHeaders()),
                clazz);
    }

    private HttpHeaders getRequiredHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "application/json");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }
}
