package com.awesome.testing.security;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import io.jsonwebtoken.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import com.awesome.testing.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.awesome.testing.dto.users.Role;

@Component
@RequiredArgsConstructor
public class JwtTokenUtil {

    @Value("${security.jwt.token.expire-length:3600000}")
    private long validityInMilliseconds; // 1h

    private final JwtParser jwtParser;
    private final SecretKeyProvider secretKeyProvider;
    private final MyUserDetails myUserDetails;

    public String createToken(String username, List<Role> roles) {
        return Jwts.builder()
                .subject(username)
                .claim("auth", getRoles(roles))
                .issuedAt(new Date())
                .expiration(getExpirationDate())
                .signWith(secretKeyProvider.getSecretKey())
                .compact();
    }

    public Authentication getAuthentication(String token) {
        UserDetails userDetails = myUserDetails.loadUserByUsername(getUsername(token));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    public String getUsername(String token) {
        return jwtParser.parseSignedClaims(token).getPayload().getSubject();
    }

    public String extractTokenFromRequest(HttpServletRequest req) {
        return Optional.ofNullable(req.getCookies())
            .stream()
            .flatMap(Arrays::stream)
            .filter(cookie -> "token".equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    public boolean validateToken(String token) {
        try {
            jwtParser.parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException("Expired or invalid JWT token", HttpStatus.FORBIDDEN);
        }
    }

    public long getTokenValidityInSeconds() {
        return validityInMilliseconds / 1000;
    }

    private Date getExpirationDate() {
        Date now = new Date();
        return new Date(now.getTime() + validityInMilliseconds);
    }

    private List<SimpleGrantedAuthority> getRoles(List<Role> roles) {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getAuthority()))
                .toList();
    }

}