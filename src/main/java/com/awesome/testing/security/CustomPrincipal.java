package com.awesome.testing.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
@AllArgsConstructor
public class CustomPrincipal {
    private final String username;
    private final UserDetails userDetails;

    public boolean isAdmin() {
        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    @Override
    public String toString() {
        return username;
    }
} 