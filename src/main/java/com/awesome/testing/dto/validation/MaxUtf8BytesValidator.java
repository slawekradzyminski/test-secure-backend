package com.awesome.testing.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, CharSequence> {

    private int max;

    @Override
    public void initialize(MaxUtf8Bytes constraint) {
        this.max = constraint.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || utf8Length(value) <= max;
    }

    public static int utf8Length(CharSequence value) {
        return value.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
