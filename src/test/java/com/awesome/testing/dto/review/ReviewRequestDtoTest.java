package com.awesome.testing.dto.review;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRequestDtoTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private static final String TITLE = "Great phone";
    private static final String BODY = "Battery lasts two days.";

    @AfterAll
    static void tearDown() {
        FACTORY.close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "  ", "\n\t"})
    void aBlankTitleFailsOnlyAsRequired(String title) {
        assertThat(messages(new ReviewRequestDto(5, title, BODY)))
                .isEqualTo(Map.of("title", "Title is required"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "         ", "\n\t"})
    void aBlankBodyFailsOnlyAsRequired(String body) {
        assertThat(messages(new ReviewRequestDto(5, TITLE, body)))
                .isEqualTo(Map.of("body", "Review text is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "ab", " a", "\na"})
    void aShortTitleFailsOnlyOnLength(String title) {
        assertThat(messages(new ReviewRequestDto(5, title, BODY)))
                .isEqualTo(Map.of("title", "Title must be between 3 and 120 characters"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "too short", "  a  b  "})
    void aShortBodyFailsOnlyOnLength(String body) {
        assertThat(messages(new ReviewRequestDto(5, TITLE, body)))
                .isEqualTo(Map.of("body", "Review text must be between 10 and 2000 characters"));
    }

    @Test
    void overlongFieldsFailOnLength() {
        assertThat(messages(new ReviewRequestDto(5, "t".repeat(121), "b".repeat(2001)))).isEqualTo(Map.of(
                "title", "Title must be between 3 and 120 characters",
                "body", "Review text must be between 10 and 2000 characters"));
    }

    @Test
    void boundaryLengthsAreAccepted() {
        assertThat(messages(new ReviewRequestDto(5, "abc", "0123456789"))).isEmpty();
        assertThat(messages(new ReviewRequestDto(5, " a\nb", "line one\nline two"))).isEmpty();
        assertThat(messages(new ReviewRequestDto(5, "t".repeat(120), "b".repeat(2000)))).isEmpty();
    }

    /** Field to messages; a field with two failing constraints would collide here and fail the test. */
    private static Map<String, String> messages(ReviewRequestDto request) {
        return VALIDATOR.validate(request).stream().collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(), ConstraintViolation::getMessage));
    }
}
