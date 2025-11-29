package com.awesome.testing.security;

import com.awesome.testing.controller.exception.CustomException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtTokenFilter jwtTokenFilter;

    @AfterEach
    void cleanContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipFilterWhenNoToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.extractTokenFromRequest(request)).thenReturn(null);

        jwtTokenFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldAuthenticateWhenTokenValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.extractTokenFromRequest(request)).thenReturn("token");
        when(jwtTokenProvider.validateToken("token")).thenReturn(true);
        var authentication = new UsernamePasswordAuthenticationToken("user", "cred");
        when(jwtTokenProvider.getAuthentication("token")).thenReturn(authentication);

        jwtTokenFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(authentication);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturnUnauthorizedWhenCustomExceptionThrown() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.extractTokenFromRequest(request)).thenReturn("bad");
        when(jwtTokenProvider.validateToken("bad"))
                .thenThrow(new CustomException("Invalid", org.springframework.http.HttpStatus.UNAUTHORIZED));

        jwtTokenFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldReturn500OnUnexpectedException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.extractTokenFromRequest(request)).thenReturn("bad");
        when(jwtTokenProvider.validateToken("bad")).thenThrow(new RuntimeException("Boom"));

        jwtTokenFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("Internal server error");
    }
}
