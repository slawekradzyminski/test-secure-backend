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
        properties.setMaxBodyLength(500);
        TrafficDataSanitizer sanitizer = new TrafficDataSanitizer(properties);

        String body = "{\"email\":\"john@example.com\",\"password\":\"secret\",\"token\":\"abc123\"}";

        Map<String, List<String>> sanitizedHeaders = sanitizer.sanitizeHeaders(Map.of(
                "Authorization", List.of("Bearer very-secret-token"),
                "X-Test", List.of("ok")
        ));
        String sanitizedBody = sanitizer.sanitizeBody(body);

        assertThat(sanitizedHeaders.get("Authorization")).containsExactly("***");
        assertThat(sanitizedHeaders.get("X-Test")).containsExactly("ok");
        assertThat(sanitizedBody).doesNotContain("john@example.com");
        assertThat(sanitizedBody).doesNotContain("secret");
        assertThat(sanitizedBody).doesNotContain("abc123");
        assertThat(sanitizedBody).contains("***");
    }

    @Test
    void shouldTruncateOversizedBodies() {
        TrafficProperties properties = new TrafficProperties();
        properties.setMaxBodyLength(10);
        TrafficDataSanitizer sanitizer = new TrafficDataSanitizer(properties);

        String sanitizedBody = sanitizer.sanitizeBody("0123456789abcdef");

        assertThat(sanitizedBody).hasSizeGreaterThan(10);
        assertThat(sanitizedBody).endsWith("...(truncated)");
    }
}
