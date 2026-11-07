package com.awesome.testing.security.mfa;

import com.awesome.testing.config.properties.MfaProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
    void shouldRejectDevelopmentSaltEvenWithStrongPassword() {
        MfaProperties properties = properties(
                "a-strong-deployment-specific-password-with-entropy",
                "8F9C82CF1D4A63B107A82FC60E0C27AB");

        assertThatThrownBy(() -> config.mfaTextEncryptor(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production MFA encryption must not use the development key");
    }

    @Test
    void shouldRejectShortPasswordWhenSecureConfigurationIsRequired() {
        MfaProperties properties = properties(
                "short-production-password",
                "0123456789abcdef0123456789abcdef");

        assertThatThrownBy(() -> config.mfaTextEncryptor(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production MFA encryption password must contain at least 32 characters");
    }

    @Test
    void shouldAcceptPasswordOfExactly32CharactersWhenSecureConfigurationIsRequired() {
        MfaProperties properties = properties(
                "deployment-password-32-chars-abc",
                "0123456789abcdef0123456789abcdef");

        assertThat(properties.getEncryptionPassword()).hasSize(32);
        assertThat(config.mfaTextEncryptor(properties)).isNotNull();
    }

    @Test
    void shouldRejectNonHexSaltWhenSecureConfigurationIsRequired() {
        MfaProperties properties = properties(
                "a-strong-deployment-specific-password-with-entropy",
                "not-a-hex-salt-but-long-enough-0123");

        assertThatThrownBy(() -> config.mfaTextEncryptor(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production MFA encryption salt must contain at least 16 random bytes in hexadecimal");
    }

    @Test
    void shouldRejectTooShortHexSaltWhenSecureConfigurationIsRequired() {
        MfaProperties properties = properties(
                "a-strong-deployment-specific-password-with-entropy",
                "0123456789abcdef");

        assertThatThrownBy(() -> config.mfaTextEncryptor(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production MFA encryption salt must contain at least 16 random bytes in hexadecimal");
    }

    @Test
    void shouldRejectMissingEncryptionMaterialInEveryEnvironment() {
        MfaProperties missingPassword = properties(" ", "0123456789abcdef0123456789abcdef");
        missingPassword.setRequireSecureEncryption(false);
        MfaProperties missingSalt = properties("a-strong-deployment-specific-password-with-entropy", "");
        missingSalt.setRequireSecureEncryption(false);

        assertThatThrownBy(() -> config.mfaTextEncryptor(missingPassword))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("APP_MFA_ENCRYPTION_PASSWORD must be configured");
        assertThatThrownBy(() -> config.mfaTextEncryptor(missingSalt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("APP_MFA_ENCRYPTION_SALT must be configured");
    }

    @Test
    void shouldAcceptStrongDeploymentSpecificEncryptionMaterial() {
        MfaProperties properties = properties(
                "a-strong-deployment-specific-password-with-entropy",
                "0123456789abcdef0123456789abcdef");

        var encryptor = config.mfaTextEncryptor(properties);
        String ciphertext = encryptor.encrypt("mfa-secret");

        assertThat(ciphertext).isNotEqualTo("mfa-secret");
        assertThat(encryptor.decrypt(ciphertext)).isEqualTo("mfa-secret");
    }

    private MfaProperties properties(String password, String salt) {
        MfaProperties properties = new MfaProperties();
        properties.setEncryptionPassword(password);
        properties.setEncryptionSalt(salt);
        properties.setRequireSecureEncryption(true);
        return properties;
    }
}
