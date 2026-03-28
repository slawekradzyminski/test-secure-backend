package com.awesome.testing.endpoints.users;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "security.rate-limit.enabled=true",
        "security.rate-limit.policies.signup-ip.capacity=1",
        "security.rate-limit.policies.signup-ip.window=1h"
})
class SignUpRateLimitTest extends HttpHelper {

    @Test
    void shouldReturn429WhenSignupRateLimitIsExceeded() {
        UserRegisterDto first = user("signup-rate-1", "signup-rate-1@example.com");
        UserRegisterDto second = user("signup-rate-2", "signup-rate-2@example.com");

        ResponseEntity<String> firstAttempt = executePost(
                "/api/v1/users/signup",
                first,
                getJsonOnlyHeaders(),
                String.class
        );
        ResponseEntity<ErrorDto> secondAttempt = executePost(
                "/api/v1/users/signup",
                second,
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        assertThat(firstAttempt.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(secondAttempt.getBody()).isNotNull();
        assertThat(secondAttempt.getBody().getMessage()).contains("Too many requests");
    }

    private static UserRegisterDto user(String username, String email) {
        return UserRegisterDto.builder()
                .username(username)
                .email(email)
                .password("password123")
                .firstName("Rate")
                .lastName("Limited")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
    }
}
