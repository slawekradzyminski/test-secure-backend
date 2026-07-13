package com.awesome.testing.traffic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficDataSanitizerTest {

    @Test
    void shouldObfuscateConfiguredSensitiveValues() {
        TrafficProperties properties = new TrafficProperties();
        properties.setObfuscateAuthorization(true);
        properties.setObfuscateEmails(true);
        properties.setObfuscateSensitiveBodyFields(true);
        properties.setMaxBodyLength(500);
        TrafficDataSanitizer sanitizer = new TrafficDataSanitizer(properties);

        String body = """
                {"email":"john@example.com","password":"password-value","token":"token-value",
                "code":"123456","secret":"BASE32SECRET","otpAuthUri":"otpauth://sensitive",
                "qrCodeDataUri":"data:image/png;base64,sensitive","challengeToken":"challenge-value",
                "recoveryCodes":["RECOVERY-ONE","RECOVERY-TWO"]}
                """;

        Map<String, List<String>> sanitizedHeaders = sanitizer.sanitizeHeaders(Map.of(
                "Authorization", List.of("Bearer very-secret-token"),
                "X-Test", List.of("ok")
        ));
        TrafficBodySanitizationResult sanitizedBody = sanitizer.sanitizeBody(body);

        assertThat(sanitizedHeaders.get("Authorization")).containsExactly("***");
        assertThat(sanitizedHeaders.get("X-Test")).containsExactly("ok");
        assertThat(sanitizedBody.body()).doesNotContain("john@example.com");
        assertThat(sanitizedBody.body()).doesNotContain(
                "password-value", "token-value", "123456", "BASE32SECRET", "otpauth://sensitive",
                "data:image/png;base64,sensitive", "challenge-value", "RECOVERY-ONE", "RECOVERY-TWO");
        assertThat(sanitizedBody.body()).contains("***");
        assertThat(sanitizedBody.truncated()).isFalse();
    }

    @Test
    void shouldLeaveSensitiveValuesVisibleWhenObfuscationIsDisabled() {
        TrafficProperties properties = new TrafficProperties();
        properties.setObfuscateAuthorization(false);
        properties.setObfuscateEmails(false);
        properties.setObfuscateSensitiveBodyFields(false);
        properties.setMaxBodyLength(500);
        TrafficDataSanitizer sanitizer = new TrafficDataSanitizer(properties);

        String body = "{\"email\":\"john@example.com\",\"password\":\"secret\",\"token\":\"abc123\"}";

        Map<String, List<String>> sanitizedHeaders = sanitizer.sanitizeHeaders(Map.of(
                "Authorization", List.of("Bearer very-secret-token"),
                "X-Test", List.of("ok")
        ));
        TrafficBodySanitizationResult sanitizedBody = sanitizer.sanitizeBody(body);

        assertThat(sanitizedHeaders.get("Authorization")).containsExactly("Bearer very-secret-token");
        assertThat(sanitizedBody.body()).contains("john@example.com");
        assertThat(sanitizedBody.body()).contains("secret");
        assertThat(sanitizedBody.body()).contains("abc123");
    }

    @Test
    void shouldTruncateOversizedBodiesWithoutInlineMarker() {
        TrafficProperties properties = new TrafficProperties();
        properties.setMaxBodyLength(10);
        TrafficDataSanitizer sanitizer = new TrafficDataSanitizer(properties);

        TrafficBodySanitizationResult sanitizedBody = sanitizer.sanitizeBody("0123456789abcdef");

        assertThat(sanitizedBody.body()).isEqualTo("0123456789");
        assertThat(sanitizedBody.truncated()).isTrue();
        assertThat(sanitizedBody.originalLength()).isEqualTo(16);
        assertThat(sanitizedBody.storedLength()).isEqualTo(10);
    }
}
