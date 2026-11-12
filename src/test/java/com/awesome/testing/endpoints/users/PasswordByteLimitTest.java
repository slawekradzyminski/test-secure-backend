package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

/** BCrypt compares only 72 bytes; passwords are limited in bytes, not characters (catalog T-009). */
@SuppressWarnings("ConstantConditions")
class PasswordByteLimitTest extends DomainHelper {

    private static final String MAX_ASCII = "p".repeat(72);

    @Test
    void signupRejectsAPasswordOver72Bytes() {
        // given: 37 two-byte characters are 74 bytes but only 37 characters
        for (String password : new String[] {"p".repeat(73), "ż".repeat(37)}) {
            UserRegisterDto user = client(password);

            // when
            ResponseEntity<Map<String, String>> response = executePost(REGISTER_ENDPOINT, user,
                    getJsonOnlyHeaders(), mapTypeReference());

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isEqualTo(Map.of("password", "Maximum password length: 72 bytes"));
        }
    }

    @Test
    void signupAcceptsAPasswordOfExactly72Bytes() {
        for (String password : new String[] {MAX_ASCII, "ż".repeat(36)}) {
            // when
            ResponseEntity<String> response = executePost(REGISTER_ENDPOINT, client(password), getJsonOnlyHeaders(),
                    String.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    @Test
    void signinWithBytesBeyondTheLimitDoesNotMatchTheTruncatedPassword() {
        // given
        UserRegisterDto user = client(MAX_ASCII);
        registerUser(user);

        // when
        ResponseEntity<ErrorDto> extra = attemptLogin(new LoginDto(user.getUsername(), MAX_ASCII + "EXTRA"),
                ErrorDto.class);
        ResponseEntity<String> exact = attemptLogin(new LoginDto(user.getUsername(), MAX_ASCII), String.class);

        // then
        assertThat(extra.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(extra.getBody().getMessage()).isEqualTo("Invalid username/password supplied");
        assertThat(exact.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void passwordResetRejectsANewPasswordOver72Bytes() {
        // given
        String password = "q".repeat(100);

        // when
        ResponseEntity<Map<String, String>> response = executePost("/api/v1/users/password/reset",
                Map.of("token", "any-token", "newPassword", password, "confirmPassword", password),
                getJsonOnlyHeaders(), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("newPassword", "Maximum password length: 72 bytes"));
    }

    private static UserRegisterDto client(String password) {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        user.setPassword(password);
        return user;
    }
}
