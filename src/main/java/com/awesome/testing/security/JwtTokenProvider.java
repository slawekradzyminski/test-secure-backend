package com.awesome.testing.security;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import com.awesome.testing.controller.exception.CustomException;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
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

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final int MINIMUM_SECURE_KEY_BYTES = 32;
    private static final Set<String> INSECURE_DEVELOPMENT_KEYS = Set.of(
            "secret-key",
            "test-key",
            "local-development-jwt-key-change-me"
    );

    @Value("${security.jwt.token.secret-key:secret-key}")
    private String secretKey;

    @Value("${security.jwt.token.expire-length:3600000}")
    private long validityInMilliseconds; // 1h

    @Value("${security.jwt.token.require-secure-key:false}")
    private boolean requireSecureKey;

    private final MyUserDetails myUserDetails;
    private SecretKey key;

    @SuppressWarnings("unused")
    @PostConstruct
    protected void init() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("JWT signing key must be configured");
        }

        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (requireSecureKey
                && (keyBytes.length < MINIMUM_SECURE_KEY_BYTES || INSECURE_DEVELOPMENT_KEYS.contains(secretKey))) {
            throw new IllegalStateException("JWT signing key must contain at least 32 random bytes");
        }

        if (keyBytes.length < MINIMUM_SECURE_KEY_BYTES) {
            int repetitions = Math.ceilDiv(MINIMUM_SECURE_KEY_BYTES, keyBytes.length);
            keyBytes = secretKey.repeat(repetitions).getBytes(StandardCharsets.UTF_8);
        }
        key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(String username, List<Role> roles) {
        Claims claims = Jwts.claims()
                .subject(username)
                .add("auth", getRoles(roles))
                .build();

        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        String username = getUsername(token);
        UserDetails userDetails = myUserDetails.loadUserByUsername(username);
        CustomPrincipal principal = new CustomPrincipal(username, userDetails);
        return new UsernamePasswordAuthenticationToken(principal, "", userDetails.getAuthorities());
    }

    public String getUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
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
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException("Invalid or expired token", HttpStatus.UNAUTHORIZED, e);
        }
    }

    private List<SimpleGrantedAuthority> getRoles(List<Role> roles) {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getAuthority()))
                .toList();
    }
}
