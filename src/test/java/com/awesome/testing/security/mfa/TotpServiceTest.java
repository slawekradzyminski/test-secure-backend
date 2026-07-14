package com.awesome.testing.security.mfa;

import com.awesome.testing.config.properties.MfaProperties;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T20:00:15Z");
    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    private TotpService service;
    private MfaProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MfaProperties();
        properties.setIssuer("Awesome Testing");
        properties.setPeriod(Duration.ofSeconds(30));
        properties.setAdjacentTimeSteps(1);
        service = new TotpService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldGenerateMicrosoftAuthenticatorCompatibleOtpAuthUri() {
        URI uri = service.createOtpAuthUri(SECRET, "alice@example.test");

        assertThat(uri.getScheme()).isEqualTo("otpauth");
        assertThat(uri.getHost()).isEqualTo("totp");
        assertThat(URLDecoder.decode(uri.getRawPath(), StandardCharsets.UTF_8))
                .isEqualTo("/Awesome Testing:alice@example.test");
        assertThat(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8))
                .contains("secret=" + SECRET)
                .contains("issuer=Awesome Testing")
                .contains("algorithm=SHA1")
                .contains("digits=6")
                .contains("period=30");
    }

    @Test
    void shouldAcceptCurrentAndAdjacentTimeSteps() {
        long currentStep = NOW.getEpochSecond() / 30;
        String previous = service.generateCode(SECRET, Instant.ofEpochSecond((currentStep - 1) * 30));
        String current = service.generateCode(SECRET, Instant.ofEpochSecond(currentStep * 30));
        String next = service.generateCode(SECRET, Instant.ofEpochSecond((currentStep + 1) * 30));

        assertThat(service.findMatchingTimeStep(SECRET, previous, null)).hasValue(currentStep - 1);
        assertThat(service.findMatchingTimeStep(SECRET, current, null)).hasValue(currentStep);
        assertThat(service.findMatchingTimeStep(SECRET, next, null)).hasValue(currentStep + 1);
    }

    @Test
    void shouldRejectReplayedOrMalformedCode() {
        long currentStep = NOW.getEpochSecond() / 30;
        String current = service.generateCode(SECRET, Instant.ofEpochSecond(currentStep * 30));

        OptionalLong replay = service.findMatchingTimeStep(SECRET, current, currentStep);

        assertThat(replay).isEmpty();
        assertThat(service.findMatchingTimeStep(SECRET, "12345", null)).isEmpty();
        assertThat(service.findMatchingTimeStep(SECRET, "abcdef", null)).isEmpty();
    }

    @Test
    void shouldGenerateA160BitBase32Secret() {
        String secret = service.generateSecret();

        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
    }
}
