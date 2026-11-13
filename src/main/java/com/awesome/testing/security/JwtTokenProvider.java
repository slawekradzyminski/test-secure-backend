package com.awesome.testing.security;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import com.awesome.testing.controller.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import com.awesome.testing.dto.user.Role;

/**
 * Issues and verifies access tokens signed with RS256. Other services (warehouse-service)
 * verify them with the public key published at {@code /.well-known/jwks.json}, so only the
 * backend holds signing material.
 *
 * <p>The private key comes from {@code security.jwt.token.signing-key} (base64 PKCS#8 DER,
 * RSA, at least 2048 bits). When it is unset and a configured key is not required, an
 * ephemeral key is generated at startup: tokens then stop validating after a restart and
 * clients refresh them with their (database-backed) refresh token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    static final int MINIMUM_KEY_BITS = 2048;
    static final String TOKEN_VERSION_CLAIM = "ver";

    @Value("${security.jwt.token.signing-key:}")
    private String signingKey;

    @Value("${security.jwt.token.expire-length:3600000}")
    private long validityInMilliseconds; // 1h

    @Value("${security.jwt.token.require-secure-key:false}")
    private boolean requireSecureKey;

    private final MyUserDetails myUserDetails;
    private RSAPrivateCrtKey privateKey;
    private RSAPublicKey publicKey;
    private String currentKeyId;

    @SuppressWarnings("unused")
    @PostConstruct
    protected void init() {
        if (signingKey == null || signingKey.isBlank()) {
            if (requireSecureKey) {
                throw new IllegalStateException("JWT signing key must be configured");
            }
            log.warn("No JWT signing key configured; using an ephemeral RSA key that changes on every restart");
            useKeyPair(generateKeyPair());
        } else {
            useKeyPair(parsePrivateKey(signingKey.trim()));
        }
        if (publicKey.getModulus().bitLength() < MINIMUM_KEY_BITS) {
            throw new IllegalStateException("JWT signing key must be an RSA key of at least 2048 bits");
        }
    }

    /** Issues an access token bound to the account's current token version (catalog X-08). */
    public String createToken(com.awesome.testing.entity.UserEntity user) {
        return createToken(user.getUsername(), user.getRoles(), user.getTokenVersion());
    }

    public String createToken(String username, List<Role> roles) {
        return createToken(username, roles, 0);
    }

    public String createToken(String username, List<Role> roles, int tokenVersion) {
        Claims claims = Jwts.claims()
                .subject(username)
                .add("auth", getRoles(roles))
                .add(TOKEN_VERSION_CLAIM, tokenVersion)
                .build();

        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .header().keyId(currentKeyId).and()
                .claims(claims)
                .issuedAt(now)
                .expiration(validity)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Loads the token's user and rejects a token issued before the user's last logout or password reset.
     * The version check happens here, so every caller (HTTP filter, STOMP, gRPC) enforces revocation.
     * Tokens issued before versions existed carry no claim and count as version 0.
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String username = claims.getSubject();
        UserDetails userDetails = myUserDetails.loadUserByUsername(username);
        if (userDetails instanceof VersionedUserDetails versioned
                && versioned.getTokenVersion() != tokenVersion(claims)) {
            throw new CustomException("Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }
        CustomPrincipal principal = new CustomPrincipal(username, userDetails);
        return new UsernamePasswordAuthenticationToken(principal, "", userDetails.getAuthorities());
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static int tokenVersion(Claims claims) {
        Object version = claims.get(TOKEN_VERSION_CLAIM);
        return version instanceof Number number ? number.intValue() : 0;
    }

    public String extractTokenFromRequest(HttpServletRequest req) {
        return Optional.ofNullable(req.getHeader("Authorization"))
                .filter(token -> token.startsWith("Bearer "))
                .map(token -> token.substring(7))
                .orElse(null);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException("Invalid or expired token", HttpStatus.UNAUTHORIZED, e);
        }
    }

    /** The verification key as a JSON Web Key Set (RFC 7517). */
    public Map<String, Object> jwks() {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", currentKeyId);
        jwk.put("n", base64Url(publicKey.getModulus()));
        jwk.put("e", base64Url(publicKey.getPublicExponent()));
        return Map.of("keys", List.of(jwk));
    }

    public String keyId() {
        return currentKeyId;
    }

    private void useKeyPair(KeyPair keyPair) {
        this.privateKey = (RSAPrivateCrtKey) keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.currentKeyId = thumbprint(publicKey);
    }

    static KeyPair parsePrivateKey(String base64Pkcs8) {
        try {
            byte[] der = Base64.getMimeDecoder().decode(stripPem(base64Pkcs8));
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (!(factory.generatePrivate(new PKCS8EncodedKeySpec(der)) instanceof RSAPrivateCrtKey crtKey)) {
                throw new IllegalStateException("JWT signing key must be an RSA private key with CRT parameters");
            }
            RSAPublicKey derivedPublic = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
            return new KeyPair(derivedPublic, crtKey);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("JWT signing key must be a base64 PKCS#8 RSA private key", e);
        }
    }

    private static String stripPem(String value) {
        return value.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "");
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MINIMUM_KEY_BITS);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA is unavailable", e);
        }
    }

    /** RFC 7638 JWK thumbprint (SHA-256 over the required members in lexicographic order). */
    static String thumbprint(RSAPublicKey key) {
        String canonical = "{\"e\":\"" + base64Url(key.getPublicExponent())
                + "\",\"kty\":\"RSA\",\"n\":\"" + base64Url(key.getModulus()) + "\"}";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private List<SimpleGrantedAuthority> getRoles(List<Role> roles) {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getAuthority()))
                .toList();
    }
}
