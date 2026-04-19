package com.awesome.testing.security.oidc;

public record OidcUserClaims(
        String subject,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean emailVerified,
        String identityProvider
) {
}
