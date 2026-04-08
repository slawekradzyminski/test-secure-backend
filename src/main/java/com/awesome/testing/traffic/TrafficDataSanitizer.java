package com.awesome.testing.traffic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TrafficDataSanitizer {

    private static final String MASK = "***";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b"
    );
    private static final Pattern PASSWORD_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"password\"\\s*:\\s*\")([^\"]*)(\")"
    );
    private static final Pattern TOKEN_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"(?:token|refreshToken|accessToken)\"\\s*:\\s*\")([^\"]*)(\")"
    );

    private final TrafficProperties properties;

    public TrafficDataSanitizer(TrafficProperties properties) {
        this.properties = properties;
    }

    public Map<String, List<String>> sanitizeHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> sanitized = new LinkedHashMap<>();
        headers.forEach((name, values) -> sanitized.put(name, sanitizeHeaderValues(name, values)));
        return sanitized;
    }

    public TrafficBodySanitizationResult sanitizeBody(String body) {
        if (body == null || body.isBlank()) {
            return TrafficBodySanitizationResult.empty();
        }

        String sanitized = body;
        if (properties.isObfuscateSensitiveBodyFields()) {
            sanitized = PASSWORD_FIELD_PATTERN.matcher(sanitized).replaceAll("$1" + MASK + "$3");
            sanitized = TOKEN_FIELD_PATTERN.matcher(sanitized).replaceAll("$1" + MASK + "$3");
        }
        if (properties.isObfuscateEmails()) {
            sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(MASK);
        }
        int originalLength = sanitized.length();
        if (sanitized.length() > properties.getMaxBodyLength()) {
            String truncated = sanitized.substring(0, properties.getMaxBodyLength());
            return new TrafficBodySanitizationResult(truncated, true, originalLength, truncated.length());
        }
        return new TrafficBodySanitizationResult(sanitized, false, originalLength, sanitized.length());
    }

    private List<String> sanitizeHeaderValues(String headerName, List<String> values) {
        if (headerName.equalsIgnoreCase("Authorization") && properties.isObfuscateAuthorization()) {
            return List.of(MASK);
        }
        if (!properties.isObfuscateEmails()) {
            return values;
        }
        return values.stream()
                .map(value -> EMAIL_PATTERN.matcher(value).replaceAll(MASK))
                .toList();
    }
}
