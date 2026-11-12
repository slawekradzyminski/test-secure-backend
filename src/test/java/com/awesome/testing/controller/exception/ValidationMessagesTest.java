package com.awesome.testing.controller.exception;

import com.awesome.testing.dto.review.ReviewRequestDto;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationMessagesTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final SpringValidatorAdapter VALIDATOR = new SpringValidatorAdapter(FACTORY.getValidator());

    @AfterAll
    static void tearDown() {
        FACTORY.close();
    }

    @Test
    void aMissingValueWinsOverAFormatErrorAndAFormatErrorOverALengthError() {
        List<ObjectError> errors = List.of(
                error("username", "Size", "Minimum username length: 4 characters"),
                error("username", "Pattern", "Bad characters"),
                error("username", "NotBlank", "Username is required"),
                error("email", "Size", "Too long"),
                error("email", "Email", "Email should be valid"));

        assertThat(ValidationMessages.byField(errors)).isEqualTo(Map.of(
                "username", "Username is required",
                "email", "Email should be valid"));
    }

    @Test
    void theChosenMessageDoesNotDependOnTheViolationOrder() {
        List<ObjectError> errors = new ArrayList<>(List.of(
                error("body", "Size", "Review text must be between 10 and 2000 characters"),
                error("body", "NotBlank", "Review text is required"),
                error("title", "VisibleText", "Title is required"),
                error("title", "MaxUtf8Bytes", "Too many bytes")));

        Map<String, String> first = ValidationMessages.byField(errors);
        Collections.reverse(errors);

        assertThat(ValidationMessages.byField(errors)).isEqualTo(first).isEqualTo(Map.of(
                "body", "Review text is required",
                "title", "Title is required"));
    }

    @Test
    void aBlankAndOverlongReviewAlwaysReportsTheRequiredMessage() {
        // T-016: 200 spaces / 2100 spaces broke both @NotBlank and @Size(max); the message used to vary
        ReviewRequestDto review = new ReviewRequestDto(5, " ".repeat(200), " ".repeat(2100));
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(review, "review");

        VALIDATOR.validate(review, result);

        assertThat(result.getErrorCount()).isEqualTo(4);
        assertThat(ValidationMessages.byField(result.getAllErrors())).isEqualTo(Map.of(
                "title", "Title is required",
                "body", "Review text is required"));
    }

    @Test
    void classLevelErrorsAreKeyedByTheObjectName() {
        assertThat(ValidationMessages.byField(List.of(new ObjectError("passwords", "Passwords do not match"))))
                .isEqualTo(Map.of("passwords", "Passwords do not match"));
    }

    private static FieldError error(String field, String code, String message) {
        return new FieldError("object", field, null, false, new String[] {code}, null, message);
    }
}
