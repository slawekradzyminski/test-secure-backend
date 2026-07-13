package com.awesome.testing.security.mfa;

import com.awesome.testing.config.properties.MfaProperties;
import com.bastiaanjansen.otp.HMACAlgorithm;
import com.bastiaanjansen.otp.SecretGenerator;
import com.bastiaanjansen.otp.TOTPGenerator;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TotpService {

    private static final int PASSWORD_LENGTH = 6;

    private final MfaProperties properties;
    @Qualifier("mfaClock")
    private final Clock clock;

    public String generateSecret() {
        return new String(SecretGenerator.generate(properties.getSecretBits()), StandardCharsets.US_ASCII);
    }

    public URI createOtpAuthUri(String secret, String accountName) {
        try {
            return generator(secret).getURI(properties.getIssuer(), accountName);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Unable to create OTPAuth URI", ex);
        }
    }

    public OptionalLong findMatchingTimeStep(String secret, String submittedCode, Long lastAcceptedTimeStep) {
        if (submittedCode == null || !submittedCode.matches("\\d{" + PASSWORD_LENGTH + "}")) {
            return OptionalLong.empty();
        }

        long periodSeconds = properties.getPeriod().toSeconds();
        long currentStep = clock.instant().getEpochSecond() / periodSeconds;
        for (int offset = 0; offset <= properties.getAdjacentTimeSteps(); offset++) {
            if (matchesStep(secret, submittedCode, currentStep - offset, lastAcceptedTimeStep, periodSeconds)) {
                return OptionalLong.of(currentStep - offset);
            }
            if (offset > 0 && matchesStep(secret, submittedCode, currentStep + offset,
                    lastAcceptedTimeStep, periodSeconds)) {
                return OptionalLong.of(currentStep + offset);
            }
        }
        return OptionalLong.empty();
    }

    public String generateCode(String secret, Instant instant) {
        return generator(secret).at(instant);
    }

    private boolean matchesStep(String secret, String submittedCode, long candidateStep,
                                Long lastAcceptedTimeStep, long periodSeconds) {
        if (candidateStep < 0 || lastAcceptedTimeStep != null && candidateStep <= lastAcceptedTimeStep) {
            return false;
        }
        String expected = generateCode(secret, Instant.ofEpochSecond(candidateStep * periodSeconds));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                submittedCode.getBytes(StandardCharsets.US_ASCII));
    }

    @SuppressWarnings("deprecation") // SHA-1 is required for broad RFC 6238 authenticator compatibility.
    private TOTPGenerator generator(String secret) {
        return new TOTPGenerator.Builder(secret.getBytes(StandardCharsets.US_ASCII))
                .withHOTPGenerator(builder -> builder
                        .withPasswordLength(PASSWORD_LENGTH)
                        .withAlgorithm(HMACAlgorithm.SHA1))
                .withPeriod(properties.getPeriod())
                .build();
    }
}
