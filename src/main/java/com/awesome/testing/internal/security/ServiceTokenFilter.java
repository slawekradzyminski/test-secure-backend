package com.awesome.testing.internal.security;

import com.awesome.testing.internal.InternalApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.awesome.testing.utils.ErrorResponseDefinition.sendErrorResponse;

/**
 * Guards {@code /internal/**}: 404 when no service token is configured, 401
 * for a missing or wrong {@code X-Service-Token}. It runs before Spring
 * Security, which permits these paths without a JWT.
 */
@RequiredArgsConstructor
public class ServiceTokenFilter extends OncePerRequestFilter {

    private final ServiceTokenGuard guard;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !InternalApi.isInternalPath(pathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        switch (guard.check(request.getHeader(InternalApi.TOKEN_HEADER))) {
            case DISABLED -> sendErrorResponse(response, HttpStatus.NOT_FOUND, "Not found");
            case REJECTED -> sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "Invalid service token");
            case ACCEPTED -> filterChain.doFilter(request, response);
        }
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }
}
