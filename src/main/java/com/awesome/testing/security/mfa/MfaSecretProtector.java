package com.awesome.testing.security.mfa;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MfaSecretProtector {

    private final TextEncryptor mfaTextEncryptor;

    public String protect(String secret) {
        return mfaTextEncryptor.encrypt(secret);
    }

    public String reveal(String ciphertext) {
        return mfaTextEncryptor.decrypt(ciphertext);
    }
}
