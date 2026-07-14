package com.awesome.testing.service.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.RefreshTokenEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.RefreshTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
                .username("client")
                .email("client@example.com")
                .password("secret")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
        ReflectionTestUtils.setField(
                refreshTokenService,
                "refreshTokenValidityInMs",
                Duration.ofHours(1).toMillis()
        );
    }

    @Test
    void shouldCreateRefreshToken() {
        mockSavePassthrough();
        IssuedRefreshToken token = refreshTokenService.createToken(user);

        assertThat(token.user()).isEqualTo(user);
        assertThat(token.value()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
    }

    @Test
    void shouldRotateTokenAndRevokePrevious() {
        mockSavePassthrough();
        RefreshTokenEntity existing = RefreshTokenEntity.builder()
                .tokenHash(refreshTokenService.hashToken("existing"))
                .familyId("family-1")
                .createdAt(Instant.now().minusSeconds(10))
                .user(user)
                .expiresAt(Instant.now().plusSeconds(120))
                .build();
        when(refreshTokenRepository.findByTokenHash(refreshTokenService.hashToken("existing")))
                .thenReturn(Optional.of(existing));

        IssuedRefreshToken rotated = refreshTokenService.rotateToken("existing");

        assertThat(existing.isRevoked()).isTrue();
        assertThat(existing.getConsumedAt()).isNotNull();
        assertThat(existing.getReplacedByTokenHash()).hasSize(64);
        assertThat(rotated.user()).isEqualTo(user);
        assertThat(rotated.value()).isNotEqualTo("existing");
        verify(refreshTokenRepository).save(eq(existing));
    }

    @Test
    void shouldFailToRotateExpiredToken() {
        RefreshTokenEntity expired = RefreshTokenEntity.builder()
                .tokenHash(refreshTokenService.hashToken("expired"))
                .familyId("family-2")
                .createdAt(Instant.now().minusSeconds(10))
                .user(user)
                .expiresAt(Instant.now().minusSeconds(5))
                .build();
        when(refreshTokenRepository.findByTokenHash(refreshTokenService.hashToken("expired")))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotateToken("expired"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void shouldRevokeTokenWhenOwnerMatches() {
        RefreshTokenEntity token = RefreshTokenEntity.builder()
                .tokenHash(refreshTokenService.hashToken("to-revoke"))
                .familyId("family-3")
                .createdAt(Instant.now())
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(refreshTokenRepository.findByTokenHash(refreshTokenService.hashToken("to-revoke")))
                .thenReturn(Optional.of(token));

        refreshTokenService.revokeToken("to-revoke", user.getUsername());

        verify(refreshTokenRepository).revokeFamily("family-3");
    }

    @Test
    void shouldThrowWhenRevokingWithDifferentUser() {
        RefreshTokenEntity token = RefreshTokenEntity.builder()
                .tokenHash(refreshTokenService.hashToken("other"))
                .familyId("family-4")
                .createdAt(Instant.now())
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(refreshTokenRepository.findByTokenHash(refreshTokenService.hashToken("other")))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.revokeToken("other", "intruder"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void shouldRevokeWholeFamilyWhenConsumedTokenIsReused() {
        RefreshTokenEntity consumed = RefreshTokenEntity.builder()
                .tokenHash(refreshTokenService.hashToken("reused"))
                .familyId("compromised-family")
                .createdAt(Instant.now().minusSeconds(10))
                .consumedAt(Instant.now().minusSeconds(5))
                .revoked(true)
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(refreshTokenRepository.findByTokenHash(refreshTokenService.hashToken("reused")))
                .thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> refreshTokenService.rotateToken("reused"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid refresh token");
        verify(refreshTokenRepository).revokeFamily("compromised-family");
    }

    @Test
    void shouldRemoveAllTokensForUser() {
        refreshTokenService.removeAllTokensForUser(user.getUsername());

        verify(refreshTokenRepository).deleteByUserUsername(user.getUsername());
    }

    private void mockSavePassthrough() {
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
