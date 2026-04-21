package com.awesome.testing.security.oidc;

import com.awesome.testing.config.properties.SsoProperties;
import com.awesome.testing.controller.exception.CustomException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcTokenVerifierTest {

    private SsoProperties properties;
    private OidcTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new SsoProperties();
        properties.setEnabled(true);
        properties.setAudience("awesome-testing-frontend");
        verifier = new OidcTokenVerifier(properties);
    }

    @Test
    void shouldRejectVerificationWhenSsoIsDisabled() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(CustomException.class)
                .hasMessage("SSO login is disabled")
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldWrapJwtDecoderFailureAsUnauthorizedToken() {
        JwtDecoder decoder = token -> {
            throw new JwtException("bad token");
        };
        ReflectionTestUtils.setField(verifier, "jwtDecoder", decoder);

        assertThatThrownBy(() -> verifier.verify("bad-token"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid SSO token")
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldMapValidJwtClaims() {
        ReflectionTestUtils.setField(verifier, "jwtDecoder", (JwtDecoder) token -> jwt(Map.of(
                "sub", "subject-123",
                "email", " client@example.com ",
                "preferred_username", " client-user ",
                "given_name", "Client",
                "family_name", "User",
                "email_verified", true,
                "identity_provider", "google"
        )));

        OidcUserClaims claims = verifier.verify("id-token");

        assertThat(claims.subject()).isEqualTo("subject-123");
        assertThat(claims.username()).isEqualTo("client-user");
        assertThat(claims.email()).isEqualTo("client@example.com");
        assertThat(claims.firstName()).isEqualTo("Client");
        assertThat(claims.lastName()).isEqualTo("User");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.identityProvider()).isEqualTo("google");
    }

    @Test
    void shouldUseEmailAsUsernameWhenUsernameClaimIsBlank() {
        ReflectionTestUtils.setField(verifier, "jwtDecoder", (JwtDecoder) token -> jwt(Map.of(
                "sub", "subject-123",
                "email", "client@example.com",
                "preferred_username", "   "
        )));

        OidcUserClaims claims = verifier.verify("id-token");

        assertThat(claims.username()).isEqualTo("client@example.com");
        assertThat(claims.emailVerified()).isFalse();
    }

    @Test
    void shouldRejectJwtMissingRequiredEmailClaim() {
        ReflectionTestUtils.setField(verifier, "jwtDecoder", (JwtDecoder) token -> jwt(Map.of(
                "sub", "subject-123"
        )));

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(CustomException.class)
                .hasMessage("SSO token is missing required claim: email")
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectJwtMissingRequiredSubjectClaim() {
        ReflectionTestUtils.setField(verifier, "jwtDecoder", (JwtDecoder) token -> jwt(Map.of(
                "email", "client@example.com"
        )));

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(CustomException.class)
                .hasMessage("SSO token is missing required claim: sub")
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectTokenWhenAudienceIsMissing() {
        OAuth2TokenValidator<Jwt> validator =
                (OAuth2TokenValidator<Jwt>) ReflectionTestUtils.invokeMethod(verifier, "audienceValidator");

        assertThat(validator).isNotNull();
        assertThat(validator.validate(jwtWithAudience(List.of("other-audience"))).hasErrors()).isTrue();
        assertThat(validator.validate(jwtWithAudience(List.of("awesome-testing-frontend"))).hasErrors()).isFalse();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("id-token")
                .header("alg", "none")
                .claims(existing -> existing.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private static Jwt jwtWithAudience(List<String> audience) {
        return Jwt.withTokenValue("id-token")
                .header("alg", "none")
                .audience(audience)
                .subject("subject-123")
                .claim("email", "client@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
