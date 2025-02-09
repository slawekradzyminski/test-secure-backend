package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static com.awesome.testing.util.UserUtil.*;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class SignUpControllerTest extends DomainHelper {

    @Test
    public void shouldRegister() {
        // given
        UserRegisterDto userRegisterDTO = getRandomUser();

        // when
        ResponseEntity<String> response = registerUser(userRegisterDTO, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldFailToRegisterExistingUsername() {
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
    public void shouldFailToRegisterUsernameTooShort() {
        // given
        UserRegisterDto user = getRandomUserWithUsername("one");

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
        UserRegisterDto user = getRandomUserWithRoles(List.of());

        // when
        ResponseEntity<Map<String, String>> response =  restTemplate.exchange(
                REGISTER_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(user, getJsonOnlyHeaders()),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("roles")).contains("At least one role must be specified");
    }

    private <T> ResponseEntity<T> registerUser(UserRegisterDto userRegisterDTO, Class<T> clazz) {
        return executePost(
                REGISTER_ENDPOINT,
                userRegisterDTO,
                getJsonOnlyHeaders(),
                clazz);
    }
}
