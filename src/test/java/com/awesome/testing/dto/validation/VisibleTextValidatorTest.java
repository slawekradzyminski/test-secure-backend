package com.awesome.testing.dto.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class VisibleTextValidatorTest {

    private final VisibleTextValidator validator = new VisibleTextValidator();

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "        ", "\t\n\r", "​​​​", "   ",
            "﻿", "ㅤㅤ", "⠀", "\u0000\u0007", "⁠ ‍"})
    void rejectsValuesWithoutAVisibleCharacter(String value) {
        assertThat(validator.isValid(value, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", " John ", "pass word", "Zażółć", "李", "​x", "😀"})
    void acceptsValuesWithAVisibleCharacter(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @org.junit.jupiter.api.Test
    void nullIsLeftToNotNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
