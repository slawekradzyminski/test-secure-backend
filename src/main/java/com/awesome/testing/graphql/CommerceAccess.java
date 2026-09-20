package com.awesome.testing.graphql;

import com.awesome.testing.security.CustomPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** One ownership policy for all GraphQL entry points. */
@Component
@Profile("graphql")
public class CommerceAccess {
    public String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomPrincipal principal)) {
            throw new AccessDeniedException("Application identity required");
        }
        return principal.getUsername();
    }

    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    public String owner(String requestedUsername) {
        String currentUsername = username();
        if (requestedUsername == null || requestedUsername.equals(currentUsername)) {
            return currentUsername;
        }
        if (!isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        if (requestedUsername.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        return requestedUsername;
    }
}
