package com.awesome.testing.security;

import com.awesome.testing.AbstractUnitTest;
import com.awesome.testing.exception.CustomException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

import java.io.IOException;

import static org.mockito.Mockito.*;

public class JwtTokenFilterTest extends AbstractUnitTest {

    private static final String LOGOUT_EXCLUDED_ENDPOINT = "/users/logout";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private HttpServletResponse httpServletResponse;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtTokenFilter jwtTokenFilter;

    @Test
    public void logoutRequestShouldBeAllowed() throws ServletException, IOException {
        // given
        when(httpServletRequest.getRequestURI()).thenReturn(LOGOUT_EXCLUDED_ENDPOINT);

        // when
        jwtTokenFilter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        // then
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    public void requestWithValidTokenShouldBeAllowed() throws ServletException, IOException {
        // given
        when(httpServletRequest.getRequestURI()).thenReturn("/users/other");
        String validToken = "validToken";
        when(jwtTokenProvider.extractTokenFromRequest(httpServletRequest)).thenReturn(validToken);
        when(jwtTokenProvider.validateToken(validToken)).thenReturn(true);

        // when
        jwtTokenFilter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        // then
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    public void requestWithInvalidTokenShouldThrow() throws ServletException, IOException {
        // given
        when(httpServletRequest.getRequestURI()).thenReturn("/users/other");
        String invalidToken = "invalidToken";
        when(jwtTokenProvider.extractTokenFromRequest(httpServletRequest)).thenReturn(invalidToken);
        when(jwtTokenProvider.validateToken(invalidToken)).thenThrow(
                new CustomException("Expired or invalid JWT token", HttpStatus.FORBIDDEN));

        // when
        jwtTokenFilter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        // then
        verify(httpServletResponse).sendError(HttpStatus.FORBIDDEN.value(), "Expired or invalid JWT token");
        verify(filterChain, never()).doFilter(httpServletRequest, httpServletResponse);
    }

}
