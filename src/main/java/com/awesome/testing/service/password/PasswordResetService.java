package com.awesome.testing.service.password;

import com.awesome.testing.config.properties.PasswordResetProperties;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.password.ForgotPasswordResponseDto;
import com.awesome.testing.dto.password.ResetPasswordRequestDto;
import com.awesome.testing.entity.PasswordResetTokenEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.PasswordResetTokenRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.EmailService;
import com.awesome.testing.service.token.PasswordResetTokenGenerator;
import com.awesome.testing.service.token.RefreshTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PasswordResetService {

    private static final String PASSWORD_RESET_REQUESTED_METRIC = "password.reset.requested";
    private static final String PASSWORD_RESET_COMPLETED_METRIC = "password.reset.completed";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final PasswordResetEmailFactory emailFactory;
    private final PasswordResetProperties properties;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    @Value("${activemq.destination}")
    private String destination;

    @Transactional
    public ForgotPasswordResponseDto requestReset(String identifier, String requestedBaseUrl,
                                                  String clientIp, String userAgent) {
        Instant now = Instant.now();
        Optional<String> maybeToken = userRepository.findByUsernameOrEmail(identifier, identifier)
                .map(user -> handleResetRequestForUser(user, requestedBaseUrl, clientIp, userAgent, now));
        meterRegistry.ifAvailable(registry -> registry.counter(PASSWORD_RESET_REQUESTED_METRIC).increment());
        return ForgotPasswordResponseDto.builder()
                .message("If the account exists, password reset instructions have been sent.")
                .token(properties.isExposeTokenInResponse() ? maybeToken.orElse(null) : null)
                .build();
    }

    private String handleResetRequestForUser(UserEntity user, String requestedBaseUrl,
                                             String clientIp, String userAgent, Instant now) {
        passwordResetTokenRepository.deleteByUserOrExpired(user, now);

        String rawToken = tokenGenerator.generateToken();
        String tokenHash = tokenGenerator.hashToken(rawToken);

        PasswordResetTokenEntity entity = PasswordResetTokenEntity.builder()
                .user(user)
                .requestedAt(now)
                .expiresAt(now.plus(properties.getTokenTtl()))
                .tokenHash(tokenHash)
                .requestIp(clientIp)
                .userAgent(userAgent)
                .build();
        passwordResetTokenRepository.save(entity);

        String resetLink = buildResetLink(requestedBaseUrl, rawToken);
        EmailDto email = emailFactory.buildResetRequestEmail(user, resetLink, properties.getTokenTtl());
        emailService.sendEmail(email, destination);

        log.info("Password reset token created for user {}", user.getUsername());
        return rawToken;
    }

    private String buildResetLink(String requestedBaseUrl, String token) {
        String baseUrl = StringUtils.hasText(requestedBaseUrl) ? requestedBaseUrl : properties.getFrontendBaseUrl();
        try {
            URI uri = new URI(baseUrl);
            if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("http")
                    || uri.getScheme().equalsIgnoreCase("https"))) {
                throw new CustomException("Reset base URL must start with http:// or https://", HttpStatus.BAD_REQUEST);
            }
            String base = uri.toString();
            String separator = base.contains("?") ? "&" : "?";
            return base + separator + "token=" + token;
        } catch (URISyntaxException e) {
            throw new CustomException("Invalid reset base URL", HttpStatus.BAD_REQUEST, e);
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequestDto request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new CustomException("Passwords do not match", HttpStatus.BAD_REQUEST);
        }

        PasswordResetTokenEntity tokenEntity = passwordResetTokenRepository.findByTokenHash(tokenGenerator.hashToken(request.getToken()))
                .orElseThrow(() -> new CustomException("Invalid password reset token", HttpStatus.BAD_REQUEST));

        Instant now = Instant.now();
        if (tokenEntity.isExpired(now)) {
            passwordResetTokenRepository.delete(tokenEntity);
            throw new CustomException("Password reset token has expired", HttpStatus.BAD_REQUEST);
        }
        if (tokenEntity.isConsumed()) {
            throw new CustomException("Password reset token was already used", HttpStatus.BAD_REQUEST);
        }

        UserEntity user = tokenEntity.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenService.removeAllTokensForUser(user.getUsername());

        tokenEntity.setConsumedAt(now);
        passwordResetTokenRepository.save(tokenEntity);

        emailService.sendEmail(emailFactory.buildResetConfirmationEmail(user), destination);
        meterRegistry.ifAvailable(registry -> registry.counter(PASSWORD_RESET_COMPLETED_METRIC).increment());
    }
}
