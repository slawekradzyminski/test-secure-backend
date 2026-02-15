package com.awesome.testing.security;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.service.UserService;
import com.awesome.testing.service.delay.DelayGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

class WebSecurityConfigTest extends HttpHelper {

    @MockitoBean
    private UserService userService;

    @TestConfiguration
    static class SecurityTestConfig {
        @Bean
        public DelayGenerator delayGenerator() {
            return () -> 0;
        }
    }

    @Test
    void shouldAllowSignupWithoutAuthentication() {
        UserRegisterDto signupRequest = UserRegisterDto.builder()
                .username("user-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com")
                .password("password123")
                .firstName("Johnn")
                .lastName("Does")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
        doNothing().when(userService).signup(any());

        ResponseEntity<String> response = executePost(
                "/users/signup",
                signupRequest,
                getJsonOnlyHeaders(),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void shouldRejectProtectedEndpointWithoutToken() {
        ResponseEntity<String> response = executeGet(
                "/api/orders",
                getJsonOnlyHeaders(),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
