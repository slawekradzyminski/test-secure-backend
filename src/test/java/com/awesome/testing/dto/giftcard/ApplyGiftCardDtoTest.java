package com.awesome.testing.dto.giftcard;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyGiftCardDtoTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void tearDown() {
        FACTORY.close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void aMissingCodeFailsExactlyOneConstraint(String code) {
        // when
        var violations = VALIDATOR.validate(new ApplyGiftCardDto(code));

        // then
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("Gift card code is required");
    }

    @ParameterizedTest
    @ValueSource(ints = {33, 100})
    void aTooLongCodeIsReportedByItsLength(int length) {
        // when
        var violations = VALIDATOR.validate(new ApplyGiftCardDto("G".repeat(length)));

        // then
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("Gift card code must be 1-32 characters");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 32})
    void aCodeOfAllowedLengthPassesValidation(int length) {
        assertThat(VALIDATOR.validate(new ApplyGiftCardDto("G".repeat(length)))).isEmpty();
    }
}
