package com.awesome.testing.controller.exception;

import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the one-message-per-field validation body. Bean Validation reports violations in no fixed
 * order, so when a value breaks several constraints (blank and too long, say) the message is chosen
 * by a fixed priority instead of by whichever violation came last: a missing value first, then a
 * wrong format, then a wrong length or range. Ties fall back to the message text.
 */
final class ValidationMessages {

    private static final Set<String> REQUIRED = Set.of("NotNull", "NotBlank", "NotEmpty", "VisibleText");
    private static final Set<String> LENGTH_OR_RANGE = Set.of("Size", "Length", "MinLengthUnlessBlank",
            "Min", "Max", "DecimalMin", "DecimalMax", "Digits", "Positive", "PositiveOrZero",
            "Negative", "NegativeOrZero", "MaxUtf8Bytes");

    private static final Comparator<ObjectError> PRIORITY = Comparator
            .comparingInt(ValidationMessages::rank)
            .thenComparing(error -> String.valueOf(error.getDefaultMessage()));

    private ValidationMessages() {
    }

    static Map<String, String> byField(List<? extends ObjectError> errors) {
        Map<String, String> messages = new LinkedHashMap<>();
        errors.stream()
                .sorted(PRIORITY)
                .forEach(error -> messages.putIfAbsent(key(error), error.getDefaultMessage()));
        return messages;
    }

    static int rank(ObjectError error) {
        String code = error.getCode();
        if (code != null && REQUIRED.contains(code)) {
            return 0;
        }
        if (code != null && LENGTH_OR_RANGE.contains(code)) {
            return 2;
        }
        return 1;
    }

    private static String key(ObjectError error) {
        return error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
    }
}
