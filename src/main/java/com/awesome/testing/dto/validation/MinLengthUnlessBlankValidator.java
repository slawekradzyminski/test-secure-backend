package com.awesome.testing.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MinLengthUnlessBlankValidator implements ConstraintValidator<MinLengthUnlessBlank, CharSequence> {

    private int min;

    @Override
    public void initialize(MinLengthUnlessBlank constraint) {
        this.min = constraint.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || value.toString().isBlank() || value.length() >= min;
    }
}
