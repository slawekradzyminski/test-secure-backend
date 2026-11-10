package com.awesome.testing.internal.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Decides whether a presented {@code X-Service-Token} belongs to one of the
 * callers an internal endpoint accepts. Every caller has its own token; a blank
 * token disables that caller. Tokens are hashed before a constant-time
 * comparison so neither the content nor the length of a secret leaks through
 * timing, and every accepted caller is compared so the timing does not reveal
 * which one matched.
 */
public final class ServiceTokenGuard {

    public enum Decision {
        /** None of the accepted callers has a token, so the endpoint does not exist. */
        DISABLED,
        /** The token is missing or matches none of the accepted callers. */
        REJECTED,
        ACCEPTED
    }

    /** SHA-256 of each configured token; callers without a token are absent. */
    private final Map<ServiceCaller, byte[]> expectedDigests = new EnumMap<>(ServiceCaller.class);

    public ServiceTokenGuard(Map<ServiceCaller, String> configuredTokens) {
        configuredTokens.forEach((caller, token) -> {
            if (token != null && !token.isBlank()) {
                expectedDigests.put(caller, digest(token));
            }
        });
    }

    public boolean isEnabled(ServiceCaller caller) {
        return expectedDigests.containsKey(caller);
    }

    public Decision check(Collection<ServiceCaller> acceptedCallers, String presentedToken) {
        boolean anyEnabled = false;
        boolean matched = false;
        byte[] presented = presentedToken == null || presentedToken.isEmpty() ? null : digest(presentedToken);
        for (ServiceCaller caller : acceptedCallers) {
            byte[] expected = expectedDigests.get(caller);
            if (expected != null) {
                anyEnabled = true;
                // Non-short-circuit so every enabled caller is compared.
                matched |= presented != null && MessageDigest.isEqual(expected, presented);
            }
        }
        if (!anyEnabled) {
            return Decision.DISABLED;
        }
        return matched ? Decision.ACCEPTED : Decision.REJECTED;
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
