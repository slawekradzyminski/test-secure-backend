package com.awesome.testing.security.mfa;

import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** New secrets use the current KDF; unprefixed ciphertext remains readable during migration. */
public final class VersionedMfaTextEncryptor implements TextEncryptor {
    private static final String PREFIX = "v2:";
    private final BytesEncryptor current;
    private final BytesEncryptor legacy;

    public VersionedMfaTextEncryptor(String password, String salt) {
        current = AesGcmBytesEncryptor.withPassword(password, salt).build();
        legacy = legacyDecryptor(password, salt);
    }

    private static BytesEncryptor legacyDecryptor(String password, String salt) {
        // Matches the retired Encryptors.delux wire format and KDF; never used for new writes.
        PBEKeySpec specification = new PBEKeySpec(password.toCharArray(), HexFormat.of().parseHex(salt), 1024, 256);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(specification).getEncoded();
            return AesGcmBytesEncryptor.withSecretKey(new SecretKeySpec(key, "AES")).build();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not initialize legacy MFA decryption", exception);
        } finally {
            specification.clearPassword();
        }
    }

    @Override
    public String encrypt(String text) {
        return PREFIX + HexFormat.of().formatHex(current.encrypt(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String decrypt(String encryptedText) {
        if (encryptedText.startsWith(PREFIX)) {
            return new String(
                    current.decrypt(HexFormat.of().parseHex(encryptedText.substring(PREFIX.length()))),
                    StandardCharsets.UTF_8);
        }
        return new String(legacy.decrypt(HexFormat.of().parseHex(encryptedText)), StandardCharsets.UTF_8);
    }
}
