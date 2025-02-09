package com.awesome.testing.security;

import com.awesome.testing.exception.CustomException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException {
        String token = jwtTokenProvider.extractTokenFromRequest(request);
        try {
            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }
            
            if (jwtTokenProvider.validateToken(token)) {
                Authentication auth = jwtTokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(auth);
                filterChain.doFilter(request, response);
                return;
            }
            
            SecurityContextHolder.clearContext();
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"message\":\"Invalid or expired token\"}");
        } catch (CustomException ex) {
            SecurityContextHolder.clearContext();
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(ex.getHttpStatus().value());
            response.getWriter().write("{\"message\":\"" + ex.getMessage() + "\"}");
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"message\":\"Internal server error\"}");
        }
    }

}
