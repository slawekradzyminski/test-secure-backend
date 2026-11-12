package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUser;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

/** Field validation of signup, sign-in and profile edit bodies (catalog T-002 to T-005). */
@SuppressWarnings("ConstantConditions")
class UserInputValidationTest extends DomainHelper {

    private static final String TOO_LONG = "x".repeat(256);

    @Test
    void signupReportsTheMaximumLengthForOverlongValues() {
        // given
        Map<String, Object> body = signupBody();
        body.put("username", TOO_LONG);
        body.put("password", TOO_LONG);
        body.put("firstName", TOO_LONG);
        body.put("lastName", TOO_LONG);

        // when
        ResponseEntity<Map<String, String>> response = signup(body);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("username", "Maximum username length: 255 characters")
                .containsEntry("firstName", "Maximum firstName length: 255 characters")
                .containsEntry("lastName", "Maximum lastName length: 255 characters")
                .containsKey("password");
    }

    @Test
    void signupKeepsTheMinimumLengthMessagesForShortValues() {
        // given: the exact body and messages of the course signup test
        Map<String, Object> body = Map.of("username", "abc", "email", "invalid-email", "password", "short",
                "firstName", "abc", "lastName", "abc");

        // when
        ResponseEntity<Map<String, String>> response = signup(body);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "username", "Minimum username length: 4 characters",
                "email", "Email should be valid",
                "password", "Minimum password length: 8 characters",
                "firstName", "Minimum firstName length: 4 characters",
                "lastName", "Minimum lastName length: 4 characters"));
    }

    @Test
    void signupRejectsAnEmptyOrBlankEmail() {
        for (String email : new String[] {"", "   "}) {
            // given
            Map<String, Object> body = signupBody();
            body.put("email", email);

            // when
            ResponseEntity<Map<String, String>> response = signup(body);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isEqualTo(Map.of("email", "Email is required"));
        }
    }

    @Test
    void signupRejectsWhitespaceOnlyAndInvisibleValues() {
        // given
        Map<String, Object> body = signupBody();
        body.put("username", "    ");
        body.put("password", "        ");
        body.put("firstName", "\u200B\u200B\u200B\u200B");
        body.put("lastName", "\u00A0\u00A0\u00A0\u00A0");

        // when
        ResponseEntity<Map<String, String>> response = signup(body);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("username", "Username is required")
                .containsEntry("password", "Password is required")
                .containsEntry("firstName", "firstName is required")
                .containsEntry("lastName", "lastName is required");
    }

    @Test
    void signupAcceptsSpacesBetweenVisibleCharacters() {
        // given
        Map<String, Object> body = signupBody();
        body.put("password", "correct horse battery staple");
        body.put("firstName", "Anna Maria");
        body.put("lastName", "van der Berg");

        // when
        ResponseEntity<String> response = executePost(REGISTER_ENDPOINT, body, getJsonOnlyHeaders(), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void profileEditRejectsWhitespaceOnlyNames() {
        // given
        UserRegisterDto user = getRandomUser();
        String token = getToken(user);

        // when
        ResponseEntity<Map<String, String>> response = executePut(getUserEndpoint(user.getUsername()),
                Map.of("email", user.getEmail(), "firstName", "    ", "lastName", "\u200B\u200B\u200B\u200B"),
                getHeadersWith(token), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "firstName", "firstName must not be blank",
                "lastName", "lastName must not be blank"));
    }

    @Test
    void signinReportsTheMaximumLengthForOverlongValues() {
        // when
        ResponseEntity<Map<String, String>> response = executePost(LOGIN_ENDPOINT,
                Map.of("username", TOO_LONG, "password", TOO_LONG), getJsonOnlyHeaders(), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "username", "Maximum username length: 255 characters",
                "password", "Maximum password length: 255 characters"));
    }

    @Test
    void signinKeepsTheMinimumLengthMessagesForShortValues() {
        // when
        ResponseEntity<Map<String, String>> response = executePost(LOGIN_ENDPOINT,
                Map.of("username", "abc", "password", "abc"), getJsonOnlyHeaders(), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "username", "Minimum username length: 4 characters",
                "password", "Minimum password length: 4 characters"));
    }

    @Test
    void profileEditReportsTheMaximumLengthForOverlongNames() {
        // given
        UserRegisterDto user = getRandomUser();
        String token = getToken(user);

        // when
        ResponseEntity<Map<String, String>> response = executePut(getUserEndpoint(user.getUsername()),
                Map.of("email", "edit-" + user.getEmail(), "firstName", TOO_LONG, "lastName", TOO_LONG),
                getHeadersWith(token), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "firstName", "Maximum firstName length: 255 characters",
                "lastName", "Maximum lastName length: 255 characters"));
    }

    private ResponseEntity<Map<String, String>> signup(Map<String, Object> body) {
        return executePost(REGISTER_ENDPOINT, body, getJsonOnlyHeaders(), mapTypeReference());
    }

    private static Map<String, Object> signupBody() {
        UserRegisterDto user = getRandomUser();
        Map<String, Object> body = new HashMap<>();
        body.put("username", user.getUsername());
        body.put("email", user.getEmail());
        body.put("password", user.getPassword());
        body.put("firstName", user.getFirstName());
        body.put("lastName", user.getLastName());
        return body;
    }
}
