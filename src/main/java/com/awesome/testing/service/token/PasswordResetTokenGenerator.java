package com.awesome.testing.service.token;

import com.awesome.testing.config.properties.PasswordResetProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetTokenGenerator {

    private final SecureRandom secureRandom = new SecureRandom();
    private final PasswordResetProperties properties;

    public PasswordResetTokenGenerator(PasswordResetProperties properties) {
        this.properties = properties;
    }

    public String generateToken() {
        byte[] randomBytes = new byte[Math.max(16, properties.getTokenByteLength())];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }
}
