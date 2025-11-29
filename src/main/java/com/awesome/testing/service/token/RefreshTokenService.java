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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${security.jwt.refresh-token.expire-length:604800000}")
    private long refreshTokenValidityInMs;

    public RefreshTokenEntity createToken(UserEntity user) {
        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(Instant.now().plusMillis(refreshTokenValidityInMs));
        refreshToken.setUser(user);
        refreshToken.setRevoked(false);
        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshTokenEntity rotateToken(String refreshTokenValue) {
        RefreshTokenEntity existingToken = getValidToken(refreshTokenValue);
        existingToken.setRevoked(true);
        refreshTokenRepository.save(existingToken);
        return createToken(existingToken.getUser());
    }

    public void revokeToken(String refreshTokenValue, String username) {
        RefreshTokenEntity token = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new CustomException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (!token.getUser().getUsername().equals(username)) {
            throw new CustomException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }

        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    public void removeAllTokensForUser(String username) {
        refreshTokenRepository.deleteByUserUsername(username);
    }

    private RefreshTokenEntity getValidToken(String refreshTokenValue) {
        RefreshTokenEntity token = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new CustomException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new CustomException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }

        return token;
    }
}
