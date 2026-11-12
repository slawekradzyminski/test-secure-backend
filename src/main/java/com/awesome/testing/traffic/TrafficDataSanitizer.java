package com.awesome.testing.traffic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Masks secrets in captured traffic before it is stored or published (catalog X-03).
 *
 * <p>Secrets are masked in every profile; only email masking stays configurable. A value is secret when
 * its JSON key, form or query parameter name, or header name matches {@link #isSecretName(String, String)}:
 * names ending in password, secret, token, code, signature, key or cvc (plus a few explicit names such as
 * {@code otpAuthUri}), except names known to carry public data such as {@code zipCode} or
 * {@code couponCode}. A bare {@code code} is a 2FA code, recovery code or gift card code everywhere except
 * on coupon and currency endpoints, where it is the public coupon or ISO currency code. Gift card codes in
 * request paths are masked too.
 *
 * <p>The same rules are applied to rows stored before they existed by {@link TrafficHistoryMasker}; bump
 * {@link #RULES_VERSION} whenever the rules change so that stored history is masked again on the next start.
 */
@Component
public class TrafficDataSanitizer {

    /** Version of the masking rules recorded on every stored traffic row. */
    public static final int RULES_VERSION = 1;

    static final String MASK = "***";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b"
    );
    /**
     * A JSON member: quoted name, colon, then a string, number, literal or array of scalars. Works on
     * truncated or otherwise invalid JSON because it never needs the whole document.
     */
    private static final Pattern JSON_MEMBER = Pattern.compile(
            "\"((?:[^\"\\\\]|\\\\.)*)\"(\\s*:\\s*)"
                    + "(\"(?:[^\"\\\\]|\\\\.)*\"?|-?\\d[\\d.eE+-]*|true|false|null|\\[[^\\[\\]{}]*]?)"
    );
    private static final Pattern FORM_PARAMETER = Pattern.compile("(^|[&;?])([^=&;]+)=([^&;]*)");
    private static final Pattern SECRET_NAME = Pattern.compile(
            "(?i).*(password|passwd|pwd|passphrase|secret|token|code|signature|apikey|api_key|api-key|"
                    + "accesskey|access_key|privatekey|private_key|cvc|cvv|cardnumber|card_number|otpauthuri|"
                    + "qrcodedatauri|authorization|cookie|otp)s?$");
    /** Name endings that carry public data although they match {@link #SECRET_NAME}. */
    private static final List<String> PUBLIC_NAME_SUFFIXES = List.of(
            "zipcode", "postalcode", "countrycode", "couponcode", "currencycode", "statuscode", "errorcode",
            "tokentype");
    private static final Set<String> SECRET_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-local-outbox-key",
            "x-payment-signature", "x-service-token", "x-webhook-signature", "x-api-key");
    private static final List<String> PUBLIC_CODE_PATHS = List.of(
            "/api/v1/coupons", "/api/v1/admin/coupons", "/api/v1/cart/coupon",
            "/api/v1/currencies", "/api/v1/admin/currencies");
    private static final Pattern GIFT_CARD_PATH = Pattern.compile("^(/api/v1/(?:admin/)?gift-cards/)([^/?]+)");

    private final TrafficProperties properties;

    public TrafficDataSanitizer(TrafficProperties properties) {
        this.properties = properties;
    }

    public Map<String, List<String>> sanitizeHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> sanitized = new LinkedHashMap<>();
        headers.forEach((name, values) -> sanitized.put(name, sanitizeHeaderValues(name, values)));
        return sanitized;
    }

    /** Masks secrets (always) and emails (when configured), then truncates to the configured length. */
    public TrafficBodySanitizationResult sanitizeBody(String path, String body) {
        if (body == null || body.isBlank()) {
            return TrafficBodySanitizationResult.empty();
        }

        String sanitized = maskStoredBody(path, body);
        int originalLength = sanitized.length();
        if (sanitized.length() > properties.getMaxBodyLength()) {
            String truncated = sanitized.substring(0, properties.getMaxBodyLength());
            return new TrafficBodySanitizationResult(truncated, true, originalLength, truncated.length());
        }
        return new TrafficBodySanitizationResult(sanitized, false, originalLength, sanitized.length());
    }

    /** Masks a body without truncating it; also used for rows stored under older rules. */
    public String maskStoredBody(String path, String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        String sanitized = maskJsonMembers(path, body);
        if (!sanitized.stripLeading().startsWith("{") && !sanitized.stripLeading().startsWith("[")) {
            sanitized = maskParameters(path, sanitized);
        }
        return maskEmails(sanitized);
    }

    /** Masks secret query parameters, keeping parameter names and order. */
    public String sanitizeQuery(String path, String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }
        return maskEmails(maskParameters(path, query));
    }

    /** Replaces a gift card code in the path, the only route that carries a secret as a path segment. */
    public String sanitizePath(String path) {
        if (path == null) {
            return null;
        }
        Matcher matcher = GIFT_CARD_PATH.matcher(path);
        return matcher.find() ? matcher.replaceFirst("$1" + Matcher.quoteReplacement(MASK)) : path;
    }

    static boolean isSecretName(String path, String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || PUBLIC_NAME_SUFFIXES.stream().anyMatch(normalized::endsWith)) {
            return false;
        }
        if (normalized.equals("code") && path != null && PUBLIC_CODE_PATHS.stream().anyMatch(path::startsWith)) {
            return false;
        }
        return SECRET_NAME.matcher(normalized).matches();
    }

    private String maskJsonMembers(String path, String body) {
        Matcher matcher = JSON_MEMBER.matcher(body);
        StringBuilder result = new StringBuilder(body.length());
        while (matcher.find()) {
            String replacement = isSecretName(path, matcher.group(1))
                    ? "\"" + matcher.group(1) + "\"" + matcher.group(2) + maskedJsonValue(matcher.group(3))
                    : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String maskedJsonValue(String value) {
        if (value.equals("null")) {
            return value;
        }
        return value.startsWith("[") ? "[\"" + MASK + "\"]" : "\"" + MASK + "\"";
    }

    private String maskParameters(String path, String value) {
        Matcher matcher = FORM_PARAMETER.matcher(value);
        StringBuilder result = new StringBuilder(value.length());
        while (matcher.find()) {
            String name = decodedName(matcher.group(2));
            String replacement = isSecretName(path, name) && !matcher.group(3).isEmpty()
                    ? matcher.group(1) + matcher.group(2) + "=" + MASK
                    : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String decodedName(String rawName) {
        try {
            return java.net.URLDecoder.decode(rawName, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return rawName;
        }
    }

    private String maskEmails(String value) {
        return properties.isObfuscateEmails() ? EMAIL_PATTERN.matcher(value).replaceAll(MASK) : value;
    }

    private List<String> sanitizeHeaderValues(String headerName, List<String> values) {
        String normalized = headerName.toLowerCase(Locale.ROOT);
        if (SECRET_HEADERS.contains(normalized) || isSecretName(null, normalized)) {
            return values.stream().map(value -> MASK).toList();
        }
        if (!properties.isObfuscateEmails()) {
            return values;
        }
        return values.stream()
                .map(value -> EMAIL_PATTERN.matcher(value).replaceAll(MASK))
                .toList();
    }
}
