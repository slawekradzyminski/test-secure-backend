package com.awesome.testing.endpoints;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.dto.ErrorDTO;
import com.awesome.testing.dto.UserDataDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static com.awesome.testing.util.UserUtil.*;
import static org.assertj.core.api.Assertions.assertThat;

public class SignUpControllerTest extends HttpHelper {

    private static final String REGISTER_ENDPOINT = "/users/signup";

    @Test
    public void shouldRegister() {
        // given
        UserDataDTO userDataDTO = getRandomUser();

        // when
        ResponseEntity<String> response = registerUser(userDataDTO, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldFailToRegisterExistingUsername() {
        // given
        UserDataDTO firstUser = getRandomUser();
        registerUser(firstUser, String.class);
        UserDataDTO secondUser = getRandomUserWithUsername(firstUser.getUsername());

        // when
        ResponseEntity<ErrorDTO> response = registerUser(secondUser, ErrorDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getMessage()).isEqualTo("Username is already in use");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldFailToRegisterUsernameTooShort() {
        // given
        UserDataDTO user = getRandomUserWithUsername("one");

        // when
        ResponseEntity<Map<String, String>> response =  restTemplate.exchange(
                REGISTER_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(user, getJsonOnlyHeaders()),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("username")).contains("username length");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldFailToRegisterWithEmptyRoles() {
        // given
        UserDataDTO user = getRandomUserWithRoles(List.of());

        // when
        ResponseEntity<Map<String, String>> response =  restTemplate.exchange(
                REGISTER_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(user, getJsonOnlyHeaders()),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("roles")).contains("at least");
    }

    private <T> ResponseEntity<T> registerUser(UserDataDTO userDataDTO, Class<T> clazz) {
        return executePost(
                REGISTER_ENDPOINT,
                userDataDTO,
                clazz);
    }
}
