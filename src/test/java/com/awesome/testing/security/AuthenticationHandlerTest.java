package com.awesome.testing.security;

import com.awesome.testing.controller.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationHandlerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthenticationHandler authenticationHandler;

    @Test
    void shouldDelegateToAuthenticationManager() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);

        authenticationHandler.authUser("john", "pwd");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void shouldMapBadCredentialsToCustomException() {
        doThrow(new BadCredentialsException("bad")).when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authenticationHandler.authUser("john", "pwd"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid username/password supplied");
    }

    @Test
    void shouldMapGenericAuthenticationException() {
        doThrow(new AuthenticationException("unauthorized") {}).when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authenticationHandler.authUser("john", "pwd"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Unauthorized");
    }
}
