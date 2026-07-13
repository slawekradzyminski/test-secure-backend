package com.awesome.testing.service.mfa;

import java.time.Instant;

public record MfaChallenge(String rawToken, Instant expiresAt) {
}
