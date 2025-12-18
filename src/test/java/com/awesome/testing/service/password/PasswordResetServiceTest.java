package com.awesome.testing.service.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.awesome.testing.config.properties.PasswordResetProperties;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.password.ForgotPasswordResponseDto;
import com.awesome.testing.dto.password.ResetPasswordRequestDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.PasswordResetTokenEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.PasswordResetTokenRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.EmailService;
import com.awesome.testing.service.token.PasswordResetTokenGenerator;
import com.awesome.testing.service.token.RefreshTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private PasswordResetTokenGenerator passwordResetTokenGenerator;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordResetEmailFactory emailFactory;
    @Mock
    private ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider;

    private PasswordResetProperties properties;
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        properties = new PasswordResetProperties();
        properties.setExposeTokenInResponse(true);
        rebuildService();
    }

    private void rebuildService() {
        passwordResetService = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                passwordResetTokenGenerator,
                passwordEncoder,
                refreshTokenService,
                emailService,
                emailFactory,
                properties,
                meterRegistryProvider
        );
        ReflectionTestUtils.setField(passwordResetService, "destination", "email");
    }

    @Test
    void shouldGenerateTokenAndSendEmailForExistingUser() {
        UserEntity user = sampleUser();
        when(userRepository.findByUsernameOrEmail("client", "client")).thenReturn(Optional.of(user));
        when(passwordResetTokenGenerator.generateToken()).thenReturn("raw-token");
        when(passwordResetTokenGenerator.hashToken("raw-token")).thenReturn("hash-token");
        when(passwordResetTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailFactory.buildResetRequestEmail(any(), anyString(), any())).thenReturn(
                EmailDto.builder().to(user.getEmail()).subject("Reset").message("Body").build()
        );

        ForgotPasswordResponseDto response = passwordResetService.requestReset("client", null, "127.0.0.1", "JUnit");

        assertThat(response.getToken()).isEqualTo("raw-token");
        verify(passwordResetTokenRepository).deleteByUserOrExpired(eq(user), any());
        verify(passwordResetTokenRepository).save(any());
        verify(emailService).sendEmail(any(EmailDto.class), eq("email"));
    }

    @Test
    void shouldNotExposeTokenWhenDisabled() {
        properties.setExposeTokenInResponse(false);
        rebuildService();

        UserEntity user = sampleUser();
        when(userRepository.findByUsernameOrEmail("client", "client")).thenReturn(Optional.of(user));
        when(passwordResetTokenGenerator.generateToken()).thenReturn("raw-token");
        when(passwordResetTokenGenerator.hashToken("raw-token")).thenReturn("hash-token");
        when(passwordResetTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailFactory.buildResetRequestEmail(any(), anyString(), any())).thenReturn(
                EmailDto.builder().to(user.getEmail()).subject("Reset").message("Body").build()
        );

        ForgotPasswordResponseDto response = passwordResetService.requestReset("client", null, "127.0.0.1", "JUnit");

        assertThat(response.getToken()).isNull();
    }

    @Test
    void shouldResetPasswordAndInvalidateRefreshTokens() {
        UserEntity user = sampleUser();

        PasswordResetTokenEntity entity = PasswordResetTokenEntity.builder()
                .tokenHash("hash-token")
                .requestedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .user(user)
                .build();

        when(passwordResetTokenGenerator.hashToken("submitted-token")).thenReturn("hash-token");
        when(passwordResetTokenRepository.findByTokenHash("hash-token")).thenReturn(Optional.of(entity));
        when(passwordEncoder.encode("newPass123")).thenReturn("encoded");
        when(emailFactory.buildResetConfirmationEmail(user)).thenReturn(
                EmailDto.builder().to(user.getEmail()).subject("Done").message("Ok").build()
        );

        ResetPasswordRequestDto request = ResetPasswordRequestDto.builder()
                .token("submitted-token")
                .newPassword("newPass123")
                .confirmPassword("newPass123")
                .build();

        passwordResetService.resetPassword(request);

        verify(passwordEncoder).encode("newPass123");
        verify(userRepository).save(user);
        verify(refreshTokenService).removeAllTokensForUser("client");
        verify(passwordResetTokenRepository).save(entity);
        verify(emailService).sendEmail(any(EmailDto.class), eq("email"));
        assertThat(entity.getConsumedAt()).isNotNull();
        assertThat(user.getPassword()).isEqualTo("encoded");
    }

    @Test
    void shouldFailWhenPasswordsDoNotMatch() {
        ResetPasswordRequestDto request = ResetPasswordRequestDto.builder()
                .token("token")
                .newPassword("abc12345")
                .confirmPassword("different")
                .build();

        assertThrows(CustomException.class, () -> passwordResetService.resetPassword(request));
        verify(passwordResetTokenRepository, never()).findByTokenHash(anyString());
    }

    private UserEntity sampleUser() {
        return UserEntity.builder()
                .username("client")
                .email("client@example.com")
                .password("secret")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
    }
}
