package com.awesome.testing.security.oidc;

import com.awesome.testing.config.properties.SsoProperties;
import com.awesome.testing.controller.exception.CustomException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real Nimbus decoder that {@link OidcTokenVerifier} builds, so signature,
 * issuer, audience and expiry validation are verified end to end against a local JWK set.
 */
class OidcTokenVerifierJwksTest {

    private static final String ISSUER = "http://localhost/realms/awesome-testing";
    private static final String AUDIENCE = "awesome-testing-frontend";

    private static RSAKey signingKey;
    private static RSAKey untrustedKey;
    private static HttpServer jwksServer;

    private OidcTokenVerifier verifier;

    @BeforeAll
    static void startJwksServer() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("trusted-key").generate();
        untrustedKey = new RSAKeyGenerator(2048).keyID("trusted-key").generate();
        byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/certs", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(jwks);
            }
        });
        jwksServer.start();
    }

    @AfterAll
    static void stopJwksServer() {
        jwksServer.stop(0);
    }

    @BeforeEach
    void setUp() {
        SsoProperties properties = new SsoProperties();
        properties.setEnabled(true);
        properties.setIssuerUri(ISSUER);
        properties.setAudience(AUDIENCE);
        properties.setJwkSetUri("http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/certs");
        verifier = new OidcTokenVerifier(properties);
    }

    @Test
    void shouldAcceptTokenSignedByTrustedKeyForExpectedIssuerAndAudience() {
        OidcUserClaims claims = verifier.verify(sign(signingKey, UnaryOperator.identity()));

        assertThat(claims.subject()).isEqualTo("subject-123");
        assertThat(claims.email()).isEqualTo("client@example.com");
    }

    @Test
    void shouldRejectTokenSignedByUntrustedKeyWithTrustedKeyId() {
        String token = sign(untrustedKey, UnaryOperator.identity());

        assertUnauthorized(token);
    }

    @Test
    void shouldRejectTokenIssuedForAnotherAudience() {
        String token = sign(signingKey, claims -> claims.audience("another-client"));

        assertUnauthorized(token);
    }

    @Test
    void shouldRejectTokenFromAnotherIssuer() {
        String token = sign(signingKey, claims -> claims.issuer("http://localhost/realms/attacker"));

        assertUnauthorized(token);
    }

    @Test
    void shouldRejectExpiredToken() {
        Instant past = Instant.now().minusSeconds(600);
        String token = sign(signingKey, claims -> claims
                .issueTime(Date.from(past.minusSeconds(300)))
                .expirationTime(Date.from(past)));

        assertUnauthorized(token);
    }

    private void assertUnauthorized(String token) {
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid SSO token")
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static String sign(RSAKey key, UnaryOperator<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("subject-123")
                .claim("email", "client@example.com")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                    customizer.apply(claims).build()
            );
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign test token", ex);
        }
    }
}
