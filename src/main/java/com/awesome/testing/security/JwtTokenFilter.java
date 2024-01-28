package com.awesome.testing.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.awesome.testing.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import static com.awesome.testing.security.PublicPaths.PUBLIC_PATHS;

@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {
    private static final PathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtTokenUtil jwtTokenUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (shouldBeBypassed(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String token = jwtTokenUtil.extractTokenFromRequest(request);
        if (token != null && tokenBlacklistService.isBlacklisted(token)) {
            forbid(response, new ApiException("Blacklisted JWT Token", HttpStatus.FORBIDDEN));
            return;
        }

        try {
            if (token != null) {
                jwtTokenUtil.validateToken(token);
                Authentication auth = jwtTokenUtil.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (ApiException ex) {
            forbid(response, ex);
            return;
        }
        chain.doFilter(request, response);
    }

    private void forbid(HttpServletResponse response, ApiException ex) throws IOException {
        SecurityContextHolder.clearContext();
        response.sendError(ex.getHttpStatus().value(), ex.getMessage());
    }

    private boolean shouldBeBypassed(String requestURI) {
        return PUBLIC_PATHS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, requestURI));
    }

}
