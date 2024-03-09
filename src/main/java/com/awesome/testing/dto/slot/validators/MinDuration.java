package com.awesome.testing.dto.slot.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = MinDurationValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MinDuration {

    String message() default "Wrong duration";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    long value();  // minimum duration in minutes
    
}