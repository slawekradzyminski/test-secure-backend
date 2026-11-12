package com.awesome.testing.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The string has at least {@link #value()} characters, unless it is null or blank. Pair it with
 * {@code @NotBlank} instead of {@code @Size(min = ...)}: with {@code @Size} blank input failed both
 * constraints and the single reported message varied between identical requests.
 */
@Documented
@Constraint(validatedBy = MinLengthUnlessBlankValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MinLengthUnlessBlank {

    int value();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
