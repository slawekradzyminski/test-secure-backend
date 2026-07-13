package com.awesome.testing.service.mfa;

import com.awesome.testing.config.properties.MfaProperties;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.mfa.MfaRecoveryCodesResponseDto;
import com.awesome.testing.dto.mfa.MfaSetupResponseDto;
import com.awesome.testing.dto.mfa.MfaStatusResponseDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.TokenPair;
import com.awesome.testing.entity.MfaChallengeEntity;
import com.awesome.testing.entity.MfaCredentialEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.MfaChallengeRepository;
import com.awesome.testing.repository.MfaCredentialRepository;
import com.awesome.testing.repository.MfaRecoveryCodeRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.security.AuthenticationHandler;
import com.awesome.testing.security.JwtTokenProvider;
import com.awesome.testing.security.mfa.MfaSecretProtector;
import com.awesome.testing.security.mfa.TotpService;
import com.awesome.testing.service.QrService;
import com.awesome.testing.service.token.RefreshTokenService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.OptionalLong;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MfaService {

    private static final String INVALID_FACTOR = "Invalid authenticator or recovery code";

    private final MfaCredentialRepository credentialRepository;
    private final MfaChallengeRepository challengeRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final UserRepository userRepository;
    private final MfaSecretProtector secretProtector;
    private final TotpService totpService;
    private final RecoveryCodeService recoveryCodeService;
    private final MfaChallengeTokenService challengeTokenService;
    private final AuthenticationHandler authenticationHandler;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final QrService qrService;
    private final MfaProperties properties;
    @Qualifier("mfaClock")
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isEnabled(UserEntity user) {
        return credentialRepository.findByUserUsername(user.getUsername())
                .map(MfaCredentialEntity::isEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public MfaStatusResponseDto status(String username) {
        return credentialRepository.findByUserUsername(username)
                .filter(MfaCredentialEntity::isEnabled)
                .map(credential -> MfaStatusResponseDto.builder()
                        .enabled(true)
                        .unusedRecoveryCodes(recoveryCodeService.countUnused(credential))
                        .build())
                .orElseGet(() -> MfaStatusResponseDto.builder().enabled(false).build());
    }

    @Transactional
    public MfaSetupResponseDto setup(String username) {
        UserEntity user = localUser(username);
        MfaCredentialEntity credential = credentialRepository.findByUserIdForUpdate(user.getId())
                .orElseGet(() -> MfaCredentialEntity.builder().user(user).build());
        if (credential.isEnabled()) {
            throw new CustomException("Two-factor authentication is already enabled", HttpStatus.CONFLICT);
        }

        Instant now = clock.instant();
        String secret = totpService.generateSecret();
        credential.setSecretCiphertext(secretProtector.protect(secret));
        credential.setCreatedAt(now);
        credential.setSetupExpiresAt(now.plus(properties.getSetupTtl()));
        credential.setConfirmedAt(null);
        credential.setLastAcceptedTimeStep(null);
        credentialRepository.save(credential);

        URI otpAuthUri = totpService.createOtpAuthUri(secret, user.getUsername());
        return MfaSetupResponseDto.builder()
                .secret(secret)
                .otpAuthUri(otpAuthUri.toString())
                .qrCodeDataUri(createQrDataUri(otpAuthUri.toString()))
                .expiresAt(credential.getSetupExpiresAt())
                .build();
    }

    @Transactional
    public MfaRecoveryCodesResponseDto confirm(String username, String code) {
        UserEntity user = localUser(username);
        MfaCredentialEntity credential = credentialRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new CustomException("Start two-factor setup first", HttpStatus.CONFLICT));
        if (credential.isEnabled()) {
            throw new CustomException("Two-factor authentication is already enabled", HttpStatus.CONFLICT);
        }
        if (credential.getSetupExpiresAt().isBefore(clock.instant())) {
            throw new CustomException("Two-factor setup has expired", HttpStatus.GONE);
        }

        long matchedStep = requireTotp(credential, code);
        credential.setEnabled(true);
        credential.setConfirmedAt(clock.instant());
        credential.setLastAcceptedTimeStep(matchedStep);
        credentialRepository.save(credential);
        List<String> recoveryCodes = recoveryCodeService.replaceCodes(credential);
        refreshTokenService.removeAllTokensForUser(username);
        return MfaRecoveryCodesResponseDto.builder().recoveryCodes(recoveryCodes).build();
    }

    @Transactional
    public MfaRecoveryCodesResponseDto regenerateRecoveryCodes(String username, String password, String code) {
        UserEntity user = localUser(username);
        authenticationHandler.authUser(username, password);
        MfaCredentialEntity credential = enabledCredentialForUpdate(user);
        credential.setLastAcceptedTimeStep(requireTotp(credential, code));
        credentialRepository.save(credential);
        return MfaRecoveryCodesResponseDto.builder()
                .recoveryCodes(recoveryCodeService.replaceCodes(credential))
                .build();
    }

    @Transactional
    public void disable(String username, String password, String code) {
        UserEntity user = localUser(username);
        authenticationHandler.authUser(username, password);
        MfaCredentialEntity credential = enabledCredentialForUpdate(user);
        requireSecondFactor(credential, code);
        challengeRepository.deleteByUser(user);
        recoveryCodeRepository.deleteByCredential(credential);
        credentialRepository.delete(credential);
        refreshTokenService.removeAllTokensForUser(username);
    }

    @Transactional
    public MfaChallenge startSignIn(UserEntity user) {
        challengeRepository.deleteByUser(user);
        String rawToken = challengeTokenService.generate();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.getChallengeTtl());
        challengeRepository.save(MfaChallengeEntity.builder()
                .tokenHash(challengeTokenService.hash(rawToken))
                .user(user)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build());
        return new MfaChallenge(rawToken, expiresAt);
    }

    @Transactional
    public LoginResponseDto completeSignIn(String challengeToken, String code) {
        MfaChallengeEntity challenge = challengeRepository
                .findByTokenHashForUpdate(challengeTokenService.hash(challengeToken))
                .orElseThrow(() -> new CustomException("Invalid or expired MFA challenge", HttpStatus.UNAUTHORIZED));
        Instant now = clock.instant();
        if (challenge.getConsumedAt() != null || !challenge.getExpiresAt().isAfter(now)) {
            throw new CustomException("Invalid or expired MFA challenge", HttpStatus.UNAUTHORIZED);
        }

        UserEntity user = challenge.getUser();
        MfaCredentialEntity credential = enabledCredentialForUpdate(user);
        requireSecondFactor(credential, code);
        challenge.setConsumedAt(now);
        challengeRepository.save(challenge);

        TokenPair tokens = issueTokens(user);
        return LoginResponseDto.from(tokens, user);
    }

    @Transactional
    public void deleteForUser(UserEntity user) {
        challengeRepository.deleteByUser(user);
        credentialRepository.findByUserIdForUpdate(user.getId()).ifPresent(credential -> {
            recoveryCodeRepository.deleteByCredential(credential);
            credentialRepository.delete(credential);
        });
    }

    private void requireSecondFactor(MfaCredentialEntity credential, String code) {
        if (code != null && code.matches("\\d{6}")) {
            credential.setLastAcceptedTimeStep(requireTotp(credential, code));
            credentialRepository.save(credential);
            return;
        }
        if (!recoveryCodeService.consume(credential, code)) {
            throw new CustomException(INVALID_FACTOR, HttpStatus.UNAUTHORIZED);
        }
    }

    private long requireTotp(MfaCredentialEntity credential, String code) {
        String secret = secretProtector.reveal(credential.getSecretCiphertext());
        OptionalLong matchedStep = totpService.findMatchingTimeStep(
                secret,
                code,
                credential.getLastAcceptedTimeStep());
        if (matchedStep.isEmpty()) {
            throw new CustomException(INVALID_FACTOR, HttpStatus.UNAUTHORIZED);
        }
        return matchedStep.getAsLong();
    }

    private MfaCredentialEntity enabledCredentialForUpdate(UserEntity user) {
        return credentialRepository.findByUserIdForUpdate(user.getId())
                .filter(MfaCredentialEntity::isEnabled)
                .orElseThrow(() -> new CustomException("Two-factor authentication is not enabled", HttpStatus.CONFLICT));
    }

    private UserEntity localUser(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("The user doesn't exist", HttpStatus.NOT_FOUND));
        if (StringUtils.hasText(user.getAuthProvider()) || StringUtils.hasText(user.getProviderSubject())) {
            throw new CustomException("Two-factor authentication is managed by the identity provider",
                    HttpStatus.CONFLICT);
        }
        return user;
    }

    private TokenPair issueTokens(UserEntity user) {
        return TokenPair.builder()
                .token(jwtTokenProvider.createToken(user.getUsername(), user.getRoles()))
                .refreshToken(refreshTokenService.createToken(user).getToken())
                .build();
    }

    private String createQrDataUri(String value) {
        BufferedImage image = qrService.generateQrCode(value);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create MFA QR code", ex);
        }
    }
}
