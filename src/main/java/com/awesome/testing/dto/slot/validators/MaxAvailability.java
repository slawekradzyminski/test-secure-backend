package com.awesome.testing.dto.slot.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = MaxAvailabilityValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxAvailability {
    String message() default "Time difference must be no more than 8 hours";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}