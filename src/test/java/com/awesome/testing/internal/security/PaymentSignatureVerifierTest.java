package com.awesome.testing.internal.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSignatureVerifierTest {

    private static final String SECRET = "whsec-test";
    private static final long NOW = 1_800_000_000L;
    private static final String BODY = "{\"requestId\":\"r\",\"amount\":10.00}";

    private final PaymentSignatureVerifier verifier =
            new PaymentSignatureVerifier(SECRET, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

    @Test
    void acceptsASignatureOfTheExactBodyWithinFiveMinutes() {
        assertThat(verifier.verify(PaymentSignatureVerifier.sign(SECRET, NOW, BODY), BODY)).isTrue();
        assertThat(verifier.verify(PaymentSignatureVerifier.sign(SECRET, NOW - 300, BODY), BODY)).isTrue();
        assertThat(verifier.verify(PaymentSignatureVerifier.sign(SECRET, NOW + 300, BODY), BODY)).isTrue();
        assertThat(verifier.isConfigured()).isTrue();
    }

    @Test
    void rejectsTimestampsMoreThanFiveMinutesAway() {
        assertThat(verifier.verify(PaymentSignatureVerifier.sign(SECRET, NOW - 301, BODY), BODY)).isFalse();
        assertThat(verifier.verify(PaymentSignatureVerifier.sign(SECRET, NOW + 301, BODY), BODY)).isFalse();
    }

    @Test
    void rejectsAnotherSecretOrAChangedBody() {
        assertThat(verifier.verify(PaymentSignatureVerifier.sign("other", NOW, BODY), BODY)).isFalse();
        assertThat(verifier.verify(PaymentSignatureVerifier.sign(SECRET, NOW, BODY), BODY + " ")).isFalse();
    }

    @Test
    void signsTheTimestampTogetherWithTheBody() {
        String signed = PaymentSignatureVerifier.sign(SECRET, NOW, BODY);
        String v1 = signed.substring(signed.indexOf("v1="));

        assertThat(signed).isEqualTo("t=" + NOW + "," + v1);
        assertThat(verifier.verify("t=" + (NOW - 1) + "," + v1, BODY)).isFalse();
    }

    @Test
    void acceptsAnyMatchingV1InAnyOrderAndToleratesSpaces() {
        String signed = PaymentSignatureVerifier.sign(SECRET, NOW, BODY);
        String v1 = signed.substring(signed.indexOf("v1="));

        assertThat(verifier.verify(v1 + ", t=" + NOW, BODY)).isTrue();
        assertThat(verifier.verify("t=" + NOW + ",v1=00ff," + v1, BODY)).isTrue();
        assertThat(verifier.verify("t=" + NOW + ",v1=zz," + v1 + ",x=1,junk", BODY)).isTrue();
    }

    @Test
    void rejectsMalformedHeaders() {
        String signed = PaymentSignatureVerifier.sign(SECRET, NOW, BODY);
        String v1 = signed.substring(signed.indexOf("v1="));

        assertThat(verifier.verify(null, BODY)).isFalse();
        assertThat(verifier.verify(signed, null)).isFalse();
        assertThat(verifier.verify("", BODY)).isFalse();
        assertThat(verifier.verify(v1, BODY)).isFalse();
        assertThat(verifier.verify("t=" + NOW, BODY)).isFalse();
        assertThat(verifier.verify("t=soon," + v1, BODY)).isFalse();
        assertThat(verifier.verify("t=" + NOW + ",t=" + NOW + "," + v1, BODY)).isFalse();
        assertThat(verifier.verify("t=" + NOW + ",v1=not-hex", BODY)).isFalse();
        assertThat(verifier.verify("=" + NOW + "," + v1, BODY)).isFalse();
    }

    @Test
    void aBlankSecretRejectsEverything() {
        PaymentSignatureVerifier unconfigured =
                new PaymentSignatureVerifier(" ", Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(new PaymentSignatureVerifier(null, Clock.systemUTC()).isConfigured()).isFalse();
        assertThat(unconfigured.verify(PaymentSignatureVerifier.sign(" ", NOW, BODY), BODY)).isFalse();
    }
}
