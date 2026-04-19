package com.awesome.testing.service;

import com.awesome.testing.config.properties.SsoProperties;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.TokenPair;
import com.awesome.testing.entity.RefreshTokenEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.security.JwtTokenProvider;
import com.awesome.testing.security.oidc.OidcTokenVerifier;
import com.awesome.testing.security.oidc.OidcUserClaims;
import com.awesome.testing.service.token.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class SsoLoginService {

    private static final int USERNAME_MIN_LENGTH = 4;
    private static final int USERNAME_MAX_LENGTH = 255;
    private static final int SUBJECT_SUFFIX_LENGTH = 6;

    private final SsoProperties ssoProperties;
    private final OidcTokenVerifier oidcTokenVerifier;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public LoginResponseDto exchange(String idToken) {
        OidcUserClaims claims = oidcTokenVerifier.verify(idToken);
        UserEntity user = findOrProvisionUser(claims);
        TokenPair tokens = createTokens(user);
        return LoginResponseDto.from(tokens, user);
    }

    private UserEntity findOrProvisionUser(OidcUserClaims claims) {
        String authProvider = resolveAuthProvider(claims);
        return userRepository.findByAuthProviderAndProviderSubject(
                        authProvider,
                        claims.subject()
                )
                .map(user -> updateProfile(user, claims))
                .orElseGet(() -> provisionUser(claims));
    }

    private String resolveAuthProvider(OidcUserClaims claims) {
        return claims.identityProvider() != null && !claims.identityProvider().isBlank()
                ? claims.identityProvider()
                : ssoProperties.getAuthProvider();
    }

    private UserEntity provisionUser(OidcUserClaims claims) {
        userRepository.findByEmail(claims.email()).ifPresent(existing -> {
            throw new CustomException(
                    "Email is already used by another login method",
                    HttpStatus.CONFLICT
            );
        });

        UserEntity user = UserEntity.builder()
                .username(uniqueUsername(claims))
                .email(claims.email())
                .password(passwordEncoder.encode(unusablePassword()))
                .roles(List.of(Role.ROLE_CLIENT))
                .firstName(claims.firstName())
                .lastName(claims.lastName())
                .authProvider(resolveAuthProvider(claims))
                .providerSubject(claims.subject())
                .emailVerified(claims.emailVerified())
                .chatSystemPrompt(UserService.DEFAULT_CHAT_SYSTEM_PROMPT.strip())
                .toolSystemPrompt(UserService.DEFAULT_TOOL_SYSTEM_PROMPT.strip())
                .build();
        return userRepository.saveAndFlush(user);
    }

    private UserEntity updateProfile(UserEntity user, OidcUserClaims claims) {
        ensureEmailCanBeUsedBy(user, claims.email());
        user.setEmail(claims.email());
        user.setFirstName(claims.firstName());
        user.setLastName(claims.lastName());
        user.setEmailVerified(claims.emailVerified());
        return userRepository.save(user);
    }

    private void ensureEmailCanBeUsedBy(UserEntity user, String email) {
        userRepository.findByEmail(email)
                .filter(existing -> !Objects.equals(existing.getId(), user.getId()))
                .ifPresent(existing -> {
                    throw new CustomException(
                            "Email is already used by another login method",
                            HttpStatus.CONFLICT
                    );
                });
    }

    private TokenPair createTokens(UserEntity user) {
        String jwt = jwtTokenProvider.createToken(user.getUsername(), user.getRoles());
        RefreshTokenEntity refreshToken = refreshTokenService.createToken(user);
        return TokenPair.builder()
                .token(jwt)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    private String uniqueUsername(OidcUserClaims claims) {
        String baseUsername = normalizeUsername(
                firstText(claims.username(), claims.email().split("@", 2)[0])
        );
        if (!userRepository.existsByUsername(baseUsername)) {
            return baseUsername;
        }

        String suffix = subjectSuffix(claims.subject());
        int baseLimit = USERNAME_MAX_LENGTH - suffix.length() - 1;
        String truncatedBase = baseUsername.substring(0, Math.min(baseUsername.length(), baseLimit));
        String candidate = truncatedBase + "-" + suffix;
        int counter = 2;
        while (userRepository.existsByUsername(candidate)) {
            String counterSuffix = suffix + counter;
            int counterBaseLimit = USERNAME_MAX_LENGTH - counterSuffix.length() - 1;
            candidate = baseUsername.substring(0, Math.min(baseUsername.length(), counterBaseLimit))
                    + "-" + counterSuffix;
            counter++;
        }
        return candidate;
    }

    private String normalizeUsername(String rawUsername) {
        String username = rawUsername.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (username.isBlank()) {
            username = "sso-user";
        }
        if (username.length() < USERNAME_MIN_LENGTH) {
            username = username + "user";
        }
        if (username.length() > USERNAME_MAX_LENGTH) {
            username = username.substring(0, USERNAME_MAX_LENGTH);
        }
        return username;
    }

    private String subjectSuffix(String subject) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedSubject = digest.digest(subject.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedSubject).substring(0, SUBJECT_SUFFIX_LENGTH);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String firstText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private String unusablePassword() {
        return "sso-" + UUID.randomUUID();
    }

}
