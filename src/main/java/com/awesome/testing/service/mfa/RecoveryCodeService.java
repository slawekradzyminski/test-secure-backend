package com.awesome.testing.service.mfa;

import com.awesome.testing.config.properties.MfaProperties;
import com.awesome.testing.entity.MfaCredentialEntity;
import com.awesome.testing.entity.MfaRecoveryCodeEntity;
import com.awesome.testing.repository.MfaRecoveryCodeRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecoveryCodeService {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int RAW_LENGTH = 20;
    private static final int SELECTOR_LENGTH = 8;

    private final MfaRecoveryCodeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom mfaSecureRandom;
    private final MfaProperties properties;
    @Qualifier("mfaClock")
    private final Clock clock;

    public List<String> replaceCodes(MfaCredentialEntity credential) {
        repository.deleteByCredential(credential);
        Instant now = clock.instant();
        List<String> rawCodes = new ArrayList<>();
        for (int index = 0; index < properties.getRecoveryCodeCount(); index++) {
            String compactCode = generateCompactCode();
            String selector = compactCode.substring(0, SELECTOR_LENGTH);
            String verifier = compactCode.substring(SELECTOR_LENGTH);
            repository.save(MfaRecoveryCodeEntity.builder()
                    .credential(credential)
                    .selector(selector)
                    .verifierHash(passwordEncoder.encode(verifier))
                    .createdAt(now)
                    .build());
            rawCodes.add(format(compactCode));
        }
        return rawCodes;
    }

    public boolean consume(MfaCredentialEntity credential, String submittedCode) {
        String compact = normalize(submittedCode);
        if (compact.length() != RAW_LENGTH) {
            return false;
        }
        String selector = compact.substring(0, SELECTOR_LENGTH);
        String verifier = compact.substring(SELECTOR_LENGTH);
        Optional<MfaRecoveryCodeEntity> maybeCode = repository.findUnusedForUpdate(credential.getId(), selector);
        if (maybeCode.isEmpty() || !passwordEncoder.matches(verifier, maybeCode.get().getVerifierHash())) {
            return false;
        }
        maybeCode.get().setUsedAt(clock.instant());
        repository.save(maybeCode.get());
        return true;
    }

    public long countUnused(MfaCredentialEntity credential) {
        return repository.countByCredentialAndUsedAtIsNull(credential);
    }

    private String generateCompactCode() {
        StringBuilder value = new StringBuilder(RAW_LENGTH);
        for (int index = 0; index < RAW_LENGTH; index++) {
            value.append(ALPHABET[mfaSecureRandom.nextInt(ALPHABET.length)]);
        }
        return value.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String format(String compactCode) {
        return String.join("-",
                compactCode.substring(0, 4),
                compactCode.substring(4, 8),
                compactCode.substring(8, 12),
                compactCode.substring(12, 16),
                compactCode.substring(16, 20));
    }
}
