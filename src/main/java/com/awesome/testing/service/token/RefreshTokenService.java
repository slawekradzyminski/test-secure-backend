package com.awesome.testing.service.token;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.entity.RefreshTokenEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${security.jwt.refresh-token.expire-length:604800000}")
    private long refreshTokenValidityInMs;

    public IssuedRefreshToken createToken(UserEntity user) {
        return createToken(user, UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = CustomException.class)
    public IssuedRefreshToken rotateToken(String refreshTokenValue) {
        RefreshTokenEntity existingToken = findToken(refreshTokenValue);
        Instant now = Instant.now();
        if (existingToken.getConsumedAt() != null) {
            refreshTokenRepository.revokeFamily(existingToken.getFamilyId());
            throw invalidToken();
        }
        if (existingToken.isRevoked() || !existingToken.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }

        IssuedTokenValue replacement = newTokenValue();
        existingToken.setRevoked(true);
        existingToken.setConsumedAt(now);
        existingToken.setReplacedByTokenHash(replacement.hash());

        RefreshTokenEntity replacementEntity = buildToken(
                existingToken.getUser(),
                existingToken.getFamilyId(),
                replacement,
                now
        );
        refreshTokenRepository.save(existingToken);
        refreshTokenRepository.save(replacementEntity);
        return new IssuedRefreshToken(replacement.value(), existingToken.getUser());
    }

    public void revokeToken(String refreshTokenValue, String username) {
        RefreshTokenEntity token = findToken(refreshTokenValue);

        if (!token.getUser().getUsername().equals(username)) {
            throw invalidToken();
        }

        refreshTokenRepository.revokeFamily(token.getFamilyId());
    }

    public void removeAllTokensForUser(String username) {
        refreshTokenRepository.deleteByUserUsername(username);
    }

    private IssuedRefreshToken createToken(UserEntity user, String familyId) {
        Instant now = Instant.now();
        IssuedTokenValue tokenValue = newTokenValue();
        refreshTokenRepository.save(buildToken(user, familyId, tokenValue, now));
        return new IssuedRefreshToken(tokenValue.value(), user);
    }

    private RefreshTokenEntity buildToken(
            UserEntity user,
            String familyId,
            IssuedTokenValue tokenValue,
            Instant now) {
        return RefreshTokenEntity.builder()
                .tokenHash(tokenValue.hash())
                .familyId(familyId)
                .createdAt(now)
                .expiresAt(now.plusMillis(refreshTokenValidityInMs))
                .user(user)
                .build();
    }

    private RefreshTokenEntity findToken(String refreshTokenValue) {
        return refreshTokenRepository.findByTokenHash(hashToken(refreshTokenValue))
                .orElseThrow(this::invalidToken);
    }

    private IssuedTokenValue newTokenValue() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        return new IssuedTokenValue(value, hashToken(value));
    }

    String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private CustomException invalidToken() {
        return new CustomException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
    }

    private record IssuedTokenValue(String value, String hash) {
    }
}
