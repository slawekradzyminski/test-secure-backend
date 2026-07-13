package com.awesome.testing.security.mfa;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.Encryptors;

import static org.assertj.core.api.Assertions.assertThat;

class MfaSecretProtectorTest {

    private final MfaSecretProtector protector = new MfaSecretProtector(Encryptors.delux(
            "unit-test-only-encryption-password",
            "8f9c82cf1d4a63b107a82fc60e0c27ab"));

    @Test
    void shouldEncryptWithAuthenticatedRandomizedCiphertextAndDecrypt() {
        String secret = "JBSWY3DPEHPK3PXP";

        String first = protector.protect(secret);
        String second = protector.protect(secret);

        assertThat(first).isNotEqualTo(secret).isNotEqualTo(second);
        assertThat(protector.reveal(first)).isEqualTo(secret);
        assertThat(protector.reveal(second)).isEqualTo(secret);
    }
}
