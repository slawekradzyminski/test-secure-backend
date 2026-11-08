package com.awesome.testing.internal.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Decides whether a presented {@code X-Service-Token} matches the configured
 * warehouse token. Both values are hashed before a constant-time comparison so
 * neither the content nor the length of the secret leaks through timing.
 */
public final class ServiceTokenGuard {

    public enum Decision {
        /** No token is configured, so the internal API does not exist. */
        DISABLED,
        /** The token is missing or does not match. */
        REJECTED,
        ACCEPTED
    }

    private static final byte[] DISABLED_DIGEST = new byte[0];

    /** SHA-256 of the configured token, or empty when the internal API is disabled. */
    private final byte[] expectedDigest;

    public ServiceTokenGuard(String configuredToken) {
        this.expectedDigest = configuredToken == null || configuredToken.isBlank()
                ? DISABLED_DIGEST
                : digest(configuredToken);
    }

    public boolean isEnabled() {
        return expectedDigest.length > 0;
    }

    public Decision check(String presentedToken) {
        if (!isEnabled()) {
            return Decision.DISABLED;
        }
        if (presentedToken == null || presentedToken.isEmpty()) {
            return Decision.REJECTED;
        }
        return MessageDigest.isEqual(expectedDigest, digest(presentedToken)) ? Decision.ACCEPTED : Decision.REJECTED;
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
