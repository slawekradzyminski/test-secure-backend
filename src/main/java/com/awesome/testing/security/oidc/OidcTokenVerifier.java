package com.awesome.testing.security.oidc;

import com.awesome.testing.config.properties.SsoProperties;
import com.awesome.testing.controller.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OidcTokenVerifier {

    private final SsoProperties ssoProperties;

    private volatile JwtDecoder jwtDecoder;

    public OidcUserClaims verify(String idToken) {
        if (!ssoProperties.isEnabled()) {
            throw new CustomException("SSO login is disabled", HttpStatus.NOT_FOUND);
        }

        try {
            Jwt jwt = getJwtDecoder().decode(idToken);
            return toClaims(jwt);
        } catch (JwtException ex) {
            throw new CustomException("Invalid SSO token", HttpStatus.UNAUTHORIZED, ex);
        }
    }

    private JwtDecoder getJwtDecoder() {
        JwtDecoder decoder = jwtDecoder;
        if (decoder == null) {
            synchronized (this) {
                decoder = jwtDecoder;
                if (decoder == null) {
                    decoder = buildJwtDecoder();
                    jwtDecoder = decoder;
                }
            }
        }
        return decoder;
    }

    private JwtDecoder buildJwtDecoder() {
        JwtDecoder decoder = hasText(ssoProperties.getJwkSetUri())
                ? NimbusJwtDecoder.withJwkSetUri(ssoProperties.getJwkSetUri()).build()
                : JwtDecoders.fromIssuerLocation(ssoProperties.getIssuerUri());
        if (decoder instanceof NimbusJwtDecoder nimbusJwtDecoder) {
            nimbusJwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(ssoProperties.getIssuerUri()),
                    audienceValidator()
            ));
        }
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> audienceValidator() {
        return jwt -> {
            List<String> audiences = jwt.getAudience();
            if (audiences.contains(ssoProperties.getAudience())) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "The required audience is missing",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        };
    }

    private OidcUserClaims toClaims(Jwt jwt) {
        String subject = requiredClaim(jwt, "sub");
        String email = requiredClaim(jwt, ssoProperties.getEmailClaim());
        return new OidcUserClaims(
                subject,
                claim(jwt, ssoProperties.getUsernameClaim()).orElse(email),
                email,
                claim(jwt, ssoProperties.getFirstNameClaim()).orElse(null),
                claim(jwt, ssoProperties.getLastNameClaim()).orElse(null),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
        );
    }

    private String requiredClaim(Jwt jwt, String claimName) {
        return claim(jwt, claimName)
                .orElseThrow(() -> new CustomException(
                        "SSO token is missing required claim: " + claimName,
                        HttpStatus.UNAUTHORIZED
                ));
    }

    private Optional<String> claim(Jwt jwt, String claimName) {
        return Optional.ofNullable(jwt.getClaimAsString(claimName))
                .map(String::trim)
                .filter(this::hasText);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
