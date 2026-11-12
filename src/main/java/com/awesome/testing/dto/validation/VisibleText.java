package com.awesome.testing.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The string contains at least one visible character. Null is valid (pair it with {@code @NotNull}
 * when the value is required). Rejects values made only of whitespace, control characters,
 * zero-width and other format characters, and the blank "filler" letters that render as nothing,
 * while spaces between visible characters stay allowed.
 */
@Documented
@Constraint(validatedBy = VisibleTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface VisibleText {

    String message();

    /** Also accept the empty string (for update fields where "" means "leave unchanged"). */
    boolean allowEmpty() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
