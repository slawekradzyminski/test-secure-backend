package com.awesome.testing.security.mfa;

import com.awesome.testing.config.properties.MfaProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaSecurityConfigTest {

    private final MfaSecurityConfig config = new MfaSecurityConfig();

    @Test
    void shouldRejectDevelopmentEncryptionMaterialWhenSecureConfigurationIsRequired() {
        MfaProperties properties = properties(
                "local-development-mfa-key-change-me",
                "8f9c82cf1d4a63b107a82fc60e0c27ab");

        assertThatThrownBy(() -> config.mfaTextEncryptor(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production MFA encryption must not use the development key");
    }

    @Test
    void shouldAcceptStrongDeploymentSpecificEncryptionMaterial() {
        MfaProperties properties = properties(
                "a-strong-deployment-specific-password-with-entropy",
                "0123456789abcdef0123456789abcdef");

        assertThatCode(() -> config.mfaTextEncryptor(properties)).doesNotThrowAnyException();
    }

    private MfaProperties properties(String password, String salt) {
        MfaProperties properties = new MfaProperties();
        properties.setEncryptionPassword(password);
        properties.setEncryptionSalt(salt);
        properties.setRequireSecureEncryption(true);
        return properties;
    }
}
