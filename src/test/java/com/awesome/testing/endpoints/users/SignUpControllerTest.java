package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.*;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

class SignUpControllerTest extends DomainHelper {

    @Test
    void shouldRegister() {
        // given
        UserRegisterDto userRegisterDto = getRandomUser();

        // when
        ResponseEntity<String> response = registerUser(userRegisterDto, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldFailToRegisterExistingUsername() {
        // given
        UserRegisterDto firstUser = getRandomUser();
        registerUser(firstUser, String.class);
        UserRegisterDto secondUser = getRandomUserWithUsername(firstUser.getUsername());

        // when
        ResponseEntity<ErrorDto> response = registerUser(secondUser, ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Username is already in use");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldFailToRegisterExistingEmail() {
        // given
        UserRegisterDto firstUser = getRandomUser();
        registerUser(firstUser, String.class);
        UserRegisterDto secondUser = getRandomUserWithEmail(firstUser.getEmail());

        // when
        ResponseEntity<ErrorDto> response = registerUser(secondUser, ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Email is already in use");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldFailToRegisterUsernameTooShort() {
        // given
        UserRegisterDto user = getRandomUserWithUsername("one");

        // when
        ResponseEntity<Map<String, String>> response = executePost(
                REGISTER_ENDPOINT,
                user,
                getJsonOnlyHeaders(),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("username")).contains("username length");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldFailToRegisterWithEmptyRoles() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of());

        // when
        ResponseEntity<Map<String, String>> response = executePost(
                REGISTER_ENDPOINT,
                user,
                getJsonOnlyHeaders(),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("roles")).contains("At least one role must be specified");
    }

    private <T> ResponseEntity<T> registerUser(UserRegisterDto userRegisterDto, Class<T> clazz) {
        return executePost(
                REGISTER_ENDPOINT,
                userRegisterDto,
                getJsonOnlyHeaders(),
                clazz);
    }
}
