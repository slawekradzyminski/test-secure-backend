package com.awesome.testing.traffic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficDataSanitizerTest {

    private static TrafficDataSanitizer sanitizer(boolean obfuscateEmails, int maxBodyLength) {
        TrafficProperties properties = new TrafficProperties();
        properties.setObfuscateEmails(obfuscateEmails);
        properties.setMaxBodyLength(maxBodyLength);
        return new TrafficDataSanitizer(properties);
    }

    @Test
    void masksSecretsInEveryProfileEvenWithoutAnyMaskingConfigured() {
        // given: default properties, as in the local and lightweight profiles
        TrafficDataSanitizer sanitizer = new TrafficDataSanitizer(new TrafficProperties());
        String body = """
                {"username":"alice","password":"password-value","token":"token-value",
                "refreshToken":"refresh-value","secret":"BASE32SECRET","otpAuthUri":"otpauth://sensitive",
                "qrCodeDataUri":"data:image/png;base64,sensitive","challengeToken":"challenge-value",
                "recoveryCodes":["RECOVERY-ONE","RECOVERY-TWO"],"code":"123456"}
                """;

        // when
        String masked = sanitizer.sanitizeBody("/api/v1/users/signin", body).body();
        Map<String, List<String>> headers = sanitizer.sanitizeHeaders(Map.of(
                "Authorization", List.of("Bearer very-secret-token"), "X-Test", List.of("ok")));

        // then
        assertThat(masked).doesNotContain("password-value", "token-value", "refresh-value", "BASE32SECRET",
                "otpauth://sensitive", "data:image/png;base64,sensitive", "challenge-value", "RECOVERY-ONE",
                "RECOVERY-TWO", "123456");
        assertThat(masked).contains("\"username\":\"alice\"", "\"recoveryCodes\":[\"***\"]", "\"code\":\"***\"");
        assertThat(headers.get("Authorization")).containsExactly("***");
        assertThat(headers.get("X-Test")).containsExactly("ok");
    }

    @Test
    void masksThePasswordResetAndPasswordChangeFieldsThatWereStoredInClear() {
        // given
        String body = """
                {"token":"reset-token","newPassword":"NewSecret123!","confirmPassword":"NewSecret123!",
                "currentPassword":"OldSecret123!"}""";

        // when
        String masked = sanitizer(false, 4000).sanitizeBody("/api/v1/users/password/reset", body).body();

        // then
        assertThat(masked).doesNotContain("reset-token", "NewSecret123!", "OldSecret123!");
        assertThat(masked).isEqualTo("""
                {"token":"***","newPassword":"***","confirmPassword":"***",
                "currentPassword":"***"}""");
    }

    @Test
    void masksGiftCardCodesInBodiesAndPaths() {
        // given
        TrafficDataSanitizer sanitizer = sanitizer(false, 4000);

        // when
        String body = sanitizer.sanitizeBody("/api/v1/cart/gift-card", "{\"code\":\"GC-7KQ2-M9XD-4HPA\"}").body();
        String order = sanitizer.sanitizeBody("/api/v1/orders/1",
                "{\"giftCardCode\":\"GC-AAAA-BBBB-CCCC\",\"couponCode\":\"WELCOME10\"}").body();
        String path = sanitizer.sanitizePath("/api/v1/gift-cards/GC-7KQ2-M9XD-4HPA");
        String adminPath = sanitizer.sanitizePath("/api/v1/admin/gift-cards/GC-7KQ2-M9XD-4HPA/disable");

        // then
        assertThat(body).isEqualTo("{\"code\":\"***\"}");
        assertThat(order).isEqualTo("{\"giftCardCode\":\"***\",\"couponCode\":\"WELCOME10\"}");
        assertThat(path).isEqualTo("/api/v1/gift-cards/***");
        assertThat(adminPath).isEqualTo("/api/v1/admin/gift-cards/***/disable");
        assertThat(sanitizer.sanitizePath("/api/v1/gift-cards")).isEqualTo("/api/v1/gift-cards");
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/cart/coupon, WELCOME10",
        "/api/v1/admin/coupons, SAVE50",
        "/api/v1/currencies, EUR",
        "/api/v1/admin/currencies/EUR, EUR"
    })
    void keepsPublicCouponAndCurrencyCodes(String path, String code) {
        String body = "{\"code\":\"" + code + "\"}";

        assertThat(sanitizer(false, 4000).sanitizeBody(path, body).body()).isEqualTo(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"zipCode", "countryCode", "couponCode", "currencyCode", "lastStatusCode", "tokenType",
        "username", "email", "clientSessionId"})
    void keepsPublicFieldsWhoseNamesResembleSecrets(String name) {
        assertThat(TrafficDataSanitizer.isSecretName("/api/v1/orders", name)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "newPassword", "confirmPassword", "currentPassword", "token",
        "refreshToken", "accessToken", "challengeToken", "secret", "clientSecret", "code", "recoveryCodes",
        "giftCardCode", "totp", "apiKey", "signature", "cardNumber", "cvc", "Authorization", "Cookie"})
    void treatsCredentialNamesAsSecret(String name) {
        assertThat(TrafficDataSanitizer.isSecretName("/api/v1/users/signin", name)).isTrue();
    }

    @Test
    void masksNumbersArraysAndTruncatedValuesAndKeepsNull() {
        // given: a numeric code, a truncated body that ends inside a secret, and a null token
        TrafficDataSanitizer sanitizer = sanitizer(false, 4000);

        // when
        String numeric = sanitizer.maskStoredBody("/x", "{\"code\": 123456, \"pin\": 1}");
        String truncated = sanitizer.maskStoredBody("/x", "{\"username\":\"a\",\"password\":\"Secret12");
        String nullToken = sanitizer.maskStoredBody("/x", "{\"token\":null}");

        // then
        assertThat(numeric).isEqualTo("{\"code\": \"***\", \"pin\": 1}");
        assertThat(truncated).isEqualTo("{\"username\":\"a\",\"password\":\"***\"");
        assertThat(nullToken).isEqualTo("{\"token\":null}");
    }

    @Test
    void masksNestedSecretsAndLeavesEscapedTextInsideOtherValuesAlone() {
        String body = "{\"user\":{\"password\":\"p1\"},\"items\":[{\"token\":\"t1\"}],"
                + "\"message\":\"say \\\"password\\\":\\\"keep\\\"\"}";

        String masked = sanitizer(false, 4000).maskStoredBody("/x", body);

        assertThat(masked).isEqualTo("{\"user\":{\"password\":\"***\"},\"items\":[{\"token\":\"***\"}],"
                + "\"message\":\"say \\\"password\\\":\\\"keep\\\"\"}");
    }

    @Test
    void masksSecretQueryAndFormParameters() {
        TrafficDataSanitizer sanitizer = sanitizer(false, 4000);

        assertThat(sanitizer.sanitizeQuery("/reset", "token=abc&page=1&new%50assword=x"))
                .isEqualTo("token=***&page=1&new%50assword=***");
        assertThat(sanitizer.maskStoredBody("/signin", "username=alice&password=Secret1"))
                .isEqualTo("username=alice&password=***");
        assertThat(sanitizer.sanitizeQuery("/products", "page=0&size=10")).isEqualTo("page=0&size=10");
    }

    @Test
    void masksCredentialHeadersOfOtherServices() {
        Map<String, List<String>> headers = sanitizer(false, 4000).sanitizeHeaders(Map.of(
                "Cookie", List.of("SESSION=abc"),
                "X-Local-Outbox-Key", List.of("outbox-key"),
                "X-Payment-Signature", List.of("sig"),
                "X-Api-Key", List.of("key"),
                "Idempotency-Key", List.of("idem-1"),
                "X-Client-Session-Id", List.of("session-1234567890")));

        assertThat(headers.get("Cookie")).containsExactly("***");
        assertThat(headers.get("X-Local-Outbox-Key")).containsExactly("***");
        assertThat(headers.get("X-Payment-Signature")).containsExactly("***");
        assertThat(headers.get("X-Api-Key")).containsExactly("***");
        assertThat(headers.get("Idempotency-Key")).containsExactly("idem-1");
        assertThat(headers.get("X-Client-Session-Id")).containsExactly("session-1234567890");
    }

    @Test
    void masksEmailsOnlyWhenConfigured() {
        String body = "{\"email\":\"john@example.com\"}";

        assertThat(sanitizer(true, 4000).sanitizeBody("/x", body).body()).isEqualTo("{\"email\":\"***\"}");
        assertThat(sanitizer(false, 4000).sanitizeBody("/x", body).body()).isEqualTo(body);
        assertThat(sanitizer(true, 4000).sanitizeHeaders(Map.of("X-Contact", List.of("owner@example.com", "safe")))
                .get("X-Contact")).containsExactly("***", "safe");
    }

    @Test
    void shouldTruncateOversizedBodiesWithoutInlineMarker() {
        TrafficBodySanitizationResult sanitizedBody = sanitizer(false, 10).sanitizeBody("/x", "0123456789abcdef");

        assertThat(sanitizedBody.body()).isEqualTo("0123456789");
        assertThat(sanitizedBody.truncated()).isTrue();
        assertThat(sanitizedBody.originalLength()).isEqualTo(16);
        assertThat(sanitizedBody.storedLength()).isEqualTo(10);
    }

    @Test
    void shouldNotTruncateBodyAtTheExactConfiguredLimit() {
        TrafficBodySanitizationResult result = sanitizer(false, 10).sanitizeBody("/x", "0123456789");

        assertThat(result.body()).isEqualTo("0123456789");
        assertThat(result.truncated()).isFalse();
        assertThat(result.originalLength()).isEqualTo(10);
        assertThat(result.storedLength()).isEqualTo(10);
    }

    @Test
    void masksBeforeTruncatingSoASecretCannotSurviveAtTheCut() {
        String body = "{\"password\":\"" + "x".repeat(40) + "\"}";

        TrafficBodySanitizationResult result = sanitizer(false, 20).sanitizeBody("/x", body);

        assertThat(result.body()).isEqualTo("{\"password\":\"***\"}");
        assertThat(result.truncated()).isFalse();
    }
}
