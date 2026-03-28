package com.awesome.testing.endpoints.users;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.password.ForgotPasswordRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "security.rate-limit.enabled=true",
        "security.rate-limit.policies.password-forgot-ip.capacity=100",
        "security.rate-limit.policies.password-forgot-ip.window=15m",
        "security.rate-limit.policies.password-forgot-identifier.capacity=1",
        "security.rate-limit.policies.password-forgot-identifier.window=30m"
})
class ForgotPasswordRateLimitTest extends HttpHelper {

    @Test
    void shouldReturn429WhenForgotPasswordRateLimitIsExceeded() {
        ForgotPasswordRequestDto request = ForgotPasswordRequestDto.builder()
                .identifier("missing-user@example.com")
                .build();

        ResponseEntity<String> firstAttempt = executePost(
                "/api/v1/users/password/forgot",
                request,
                getJsonOnlyHeaders(),
                String.class
        );
        ResponseEntity<ErrorDto> secondAttempt = executePost(
                "/api/v1/users/password/forgot",
                request,
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        assertThat(firstAttempt.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(secondAttempt.getBody()).isNotNull();
        assertThat(secondAttempt.getBody().getMessage()).contains("Too many requests");
    }
}
