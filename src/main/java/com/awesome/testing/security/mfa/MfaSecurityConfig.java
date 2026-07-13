package com.awesome.testing.security.mfa;

import com.awesome.testing.config.properties.MfaProperties;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(MfaProperties.class)
public class MfaSecurityConfig {

    private static final String DEVELOPMENT_PASSWORD = "local-development-mfa-key-change-me";
    private static final String DEVELOPMENT_SALT = "8f9c82cf1d4a63b107a82fc60e0c27ab";

    @Bean
    TextEncryptor mfaTextEncryptor(MfaProperties properties) {
        if (!StringUtils.hasText(properties.getEncryptionPassword())) {
            throw new IllegalStateException("APP_MFA_ENCRYPTION_PASSWORD must be configured");
        }
        if (!StringUtils.hasText(properties.getEncryptionSalt())) {
            throw new IllegalStateException("APP_MFA_ENCRYPTION_SALT must be configured");
        }
        if (properties.isRequireSecureEncryption()
                && (DEVELOPMENT_PASSWORD.equals(properties.getEncryptionPassword())
                || DEVELOPMENT_SALT.equalsIgnoreCase(properties.getEncryptionSalt()))) {
            throw new IllegalStateException("Production MFA encryption must not use the development key");
        }
        if (properties.isRequireSecureEncryption() && properties.getEncryptionPassword().length() < 32) {
            throw new IllegalStateException("Production MFA encryption password must contain at least 32 characters");
        }
        if (properties.isRequireSecureEncryption() && !properties.getEncryptionSalt().matches("[0-9a-fA-F]{32,}")) {
            throw new IllegalStateException("Production MFA encryption salt must contain at least 16 random bytes in hexadecimal");
        }
        return Encryptors.delux(properties.getEncryptionPassword(), properties.getEncryptionSalt());
    }

    @Bean
    SecureRandom mfaSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    Clock mfaClock() {
        return Clock.systemUTC();
    }
}
