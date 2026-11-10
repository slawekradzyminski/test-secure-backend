package com.awesome.testing.service.webhook;

import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Creates signing secrets ({@code whsec_} plus 32 random bytes, base64url) and keeps them
 * encrypted at rest with the application's text encryptor (the MFA key).
 */
@Component
public class WebhookSecrets {

    static final String PREFIX = "whsec_";
    private static final int SECRET_BYTES = 32;

    private final TextEncryptor encryptor;
    private final SecureRandom random = new SecureRandom();

    public WebhookSecrets(TextEncryptor mfaTextEncryptor) {
        this.encryptor = mfaTextEncryptor;
    }

    public String generate() {
        return PREFIX + randomToken(SECRET_BYTES);
    }

    public String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    public String protect(String secret) {
        return encryptor.encrypt(secret);
    }

    public String reveal(String stored) {
        return encryptor.decrypt(stored);
    }
}
