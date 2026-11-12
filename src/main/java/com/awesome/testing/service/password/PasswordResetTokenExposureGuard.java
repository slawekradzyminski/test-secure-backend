package com.awesome.testing.service.password;

import com.awesome.testing.config.properties.PasswordResetProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Refuses to start when {@code password-reset.expose-token-in-response} is on in a shared environment.
 * Echoing the reset token lets anyone reset any account and reveals whether an account exists, so it
 * is meant only for local and lightweight stacks (the {@code local} profile) used in exercises. The
 * production ({@code server}) and AI Testers ({@code aitesters}) profiles must keep it off, even when
 * combined with {@code local} or switched on through {@code PASSWORD_RESET_EXPOSE_TOKEN_IN_RESPONSE}.
 */
@Component
@RequiredArgsConstructor
public class PasswordResetTokenExposureGuard {

    static final Set<String> SHARED_PROFILES = Set.of("server", "aitesters");

    private final PasswordResetProperties properties;
    private final Environment environment;

    @PostConstruct
    void verify() {
        check(properties.isExposeTokenInResponse(), environment.getActiveProfiles());
    }

    static void check(boolean exposeTokenInResponse, String... activeProfiles) {
        if (!exposeTokenInResponse) {
            return;
        }
        List<String> shared = Arrays.stream(activeProfiles).filter(SHARED_PROFILES::contains).sorted().toList();
        if (!shared.isEmpty()) {
            throw new IllegalStateException("password-reset.expose-token-in-response must be false with the "
                    + String.join(", ", shared) + " profile: it returns reset tokens to any caller");
        }
    }
}
