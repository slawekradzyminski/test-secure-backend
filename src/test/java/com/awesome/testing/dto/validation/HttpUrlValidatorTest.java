package com.awesome.testing.dto.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HttpUrlValidatorTest {

    private final HttpUrlValidator validator = new HttpUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {"https://not a host", "https://", "http://", "https:///path", "not-a-url",
            "ftp://example.com/a.png", "https//example.com", "https://exa mple.com", "javascript:alert(1)",
            "https://example.com/a b.png", "https://example.com/\n", "//example.com/a.png", "https://:80/a"})
    void rejectsValuesThatAreNotAbsoluteHttpUrlsWithAHost(String value) {
        assertThat(validator.isValid(value, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/iphone13.jpg", "http://localhost:8081/img.png",
            "HTTPS://EXAMPLE.COM/A.PNG", "https://cdn.example.com/a.png?w=200&h=200", "https://my_bucket.example.com/x",
            "https://example.com/%20space.png", "https://192.168.0.1/a"})
    void acceptsAbsoluteHttpUrls(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @Test
    void emptyAndNullMeanNoImage() {
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
