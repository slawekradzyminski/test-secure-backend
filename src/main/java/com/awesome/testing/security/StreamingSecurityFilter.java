package com.awesome.testing.security;

import com.awesome.testing.dto.user.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class StreamingSecurityFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        if (!path.startsWith("/api/ollama")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = jwtTokenProvider.extractTokenFromRequest(request);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Authentication auth = jwtTokenProvider.getAuthentication(token);
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            
            boolean hasRequiredRole = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals(Role.ROLE_CLIENT.name()) || role.equals(Role.ROLE_ADMIN.name()));

            if (hasRequiredRole) {
                SecurityContextHolder.getContext().setAuthentication(auth);
                filterChain.doFilter(request, response);
                return;
            }
            
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"message\":\"Access denied\"}");
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"message\":\"Unauthorized\"}");
        }
    }
} 