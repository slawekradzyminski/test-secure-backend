package com.awesome.testing.security;

import com.awesome.testing.AbstractUnitTest;
import com.awesome.testing.exception.ApiException;
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
    private JwtTokenUtil jwtTokenUtil;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private HttpServletResponse httpServletResponse;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

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
        when(jwtTokenUtil.extractTokenFromRequest(httpServletRequest)).thenReturn(validToken);
        when(jwtTokenUtil.validateToken(validToken)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(validToken)).thenReturn(false);

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
        when(jwtTokenUtil.extractTokenFromRequest(httpServletRequest)).thenReturn(invalidToken);
        when(jwtTokenUtil.validateToken(invalidToken)).thenThrow(
                new ApiException("Expired or invalid JWT token", HttpStatus.FORBIDDEN));

        // when
        jwtTokenFilter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        // then
        verify(httpServletResponse).sendError(HttpStatus.FORBIDDEN.value(), "Expired or invalid JWT token");
        verify(filterChain, never()).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    public void blacklistedTokenShouldThrow() throws ServletException, IOException {
        // given
        when(httpServletRequest.getRequestURI()).thenReturn("/users/other");
        String blacklistedToken = "invalidToken";
        when(jwtTokenUtil.extractTokenFromRequest(httpServletRequest)).thenReturn(blacklistedToken);
        when(tokenBlacklistService.isBlacklisted(blacklistedToken)).thenReturn(true);

        // when
        jwtTokenFilter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        // then
        verify(httpServletResponse).sendError(HttpStatus.FORBIDDEN.value(), "Blacklisted JWT Token");
        verify(filterChain, never()).doFilter(httpServletRequest, httpServletResponse);
    }

}
