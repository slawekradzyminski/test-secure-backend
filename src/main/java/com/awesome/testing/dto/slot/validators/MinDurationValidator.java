package com.awesome.testing.dto.slot.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Duration;

public class MinDurationValidator implements ConstraintValidator<MinDuration, Duration> {

    private long minDurationInMinutes;

    @Override
    public void initialize(MinDuration constraintAnnotation) {
        minDurationInMinutes = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(Duration value, ConstraintValidatorContext context) {
        return value.toMinutes() >= minDurationInMinutes;
    }
}