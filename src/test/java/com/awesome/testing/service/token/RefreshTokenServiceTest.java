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
        RefreshTokenEntity token = refreshTokenService.createToken(user);

        assertThat(token.getUser()).isEqualTo(user);
        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getExpiresAt()).isAfter(Instant.now());
        assertThat(token.isRevoked()).isFalse();
        verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
    }

    @Test
    void shouldRotateTokenAndRevokePrevious() {
        mockSavePassthrough();
        RefreshTokenEntity existing = RefreshTokenEntity.builder()
                .token("existing")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(120))
                .build();
        when(refreshTokenRepository.findByToken("existing")).thenReturn(Optional.of(existing));

        RefreshTokenEntity rotated = refreshTokenService.rotateToken("existing");

        assertThat(existing.isRevoked()).isTrue();
        assertThat(rotated.getUser()).isEqualTo(user);
        assertThat(rotated.getToken()).isNotEqualTo("existing");
        verify(refreshTokenRepository).save(eq(existing));
    }

    @Test
    void shouldFailToRotateExpiredToken() {
        RefreshTokenEntity expired = RefreshTokenEntity.builder()
                .token("expired")
                .user(user)
                .expiresAt(Instant.now().minusSeconds(5))
                .build();
        when(refreshTokenRepository.findByToken("expired")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotateToken("expired"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void shouldRevokeTokenWhenOwnerMatches() {
        mockSavePassthrough();
        RefreshTokenEntity token = RefreshTokenEntity.builder()
                .token("to-revoke")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(refreshTokenRepository.findByToken("to-revoke")).thenReturn(Optional.of(token));

        refreshTokenService.revokeToken("to-revoke", user.getUsername());

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void shouldThrowWhenRevokingWithDifferentUser() {
        RefreshTokenEntity token = RefreshTokenEntity.builder()
                .token("other")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(refreshTokenRepository.findByToken("other")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.revokeToken("other", "intruder"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid refresh token");
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
