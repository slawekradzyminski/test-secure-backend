package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.LoginDto;
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
        "security.rate-limit.policies.signin-ip.capacity=100",
        "security.rate-limit.policies.signin-ip.window=5m",
        "security.rate-limit.policies.signin-username.capacity=1",
        "security.rate-limit.policies.signin-username.window=15m",
        "security.rate-limit.policies.signin-ip-username.capacity=1",
        "security.rate-limit.policies.signin-ip-username.window=15m"
})
class SignInRateLimitTest extends DomainHelper {

    @Test
    void shouldReturn429WhenSigninRateLimitIsExceeded() {
        UserRegisterDto user = UserRegisterDto.builder()
                .username("rate-limit-user")
                .email("rate-limit-user@example.com")
                .password("password123")
                .firstName("Rate")
                .lastName("Limited")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();

        ResponseEntity<String> signupResponse = executePost(
                REGISTER_ENDPOINT,
                user,
                getJsonOnlyHeaders(),
                String.class
        );
        assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ErrorDto> firstAttempt = attemptLogin(
                new LoginDto(user.getUsername(), "wrong-password"),
                ErrorDto.class
        );
        ResponseEntity<ErrorDto> secondAttempt = attemptLogin(
                new LoginDto(user.getUsername(), "wrong-password"),
                ErrorDto.class
        );

        assertThat(firstAttempt.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(secondAttempt.getBody()).isNotNull();
        assertThat(secondAttempt.getBody().getMessage()).contains("Too many requests");
    }
}
