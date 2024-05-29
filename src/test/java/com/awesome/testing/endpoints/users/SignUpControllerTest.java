package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.ErrorDTO;
import com.awesome.testing.dto.users.UserRegisterDTO;
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

public class SignUpControllerTest extends DomainHelper {

    @Test
    public void shouldRegister() {
        // given
        UserRegisterDTO userRegisterDTO = getRandomUser();

        // when
        ResponseEntity<?> response = registerUser(userRegisterDTO, Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNull();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldFailToRegisterExistingUsername() {
        // given
        UserRegisterDTO firstUser = getRandomUser();
        registerUser(firstUser, String.class);
        UserRegisterDTO secondUser = getRandomUserWithUsername(firstUser.getUsername());

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
        UserRegisterDTO user = getRandomUserWithUsername("one");

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
        UserRegisterDTO user = getRandomUserWithRoles(List.of());

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

    private <T> ResponseEntity<T> registerUser(UserRegisterDTO userRegisterDTO, Class<T> clazz) {
        return executePost(
                REGISTER_ENDPOINT,
                userRegisterDTO,
                getJsonOnlyHeaders(),
                clazz);
    }
}
