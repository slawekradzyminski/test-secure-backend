package com.awesome.testing.controller.exception;

import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Turns an unreadable request body into a validation body without echoing parser internals. */
final class UnreadableBodyMessages {

    static final String MALFORMED = "Malformed JSON request body";
    static final String MISSING = "Request body is required";
    static final String INVALID = "Invalid value";

    private UnreadableBodyMessages() {
    }

    static Map<String, String> forException(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof MismatchedInputException mismatch) {
            String field = path(mismatch);
            String expected = expected(mismatch.getTargetType());
            if (!field.isEmpty()) {
                return Map.of(field, expected);
            }
            if (!INVALID.equals(expected)) {
                return Map.of("error", "Request body " + Character.toLowerCase(expected.charAt(0)) + expected.substring(1));
            }
        }
        if (cause == null && ex.getMessage() != null && ex.getMessage().startsWith("Required request body is missing")) {
            return Map.of("error", MISSING);
        }
        return Map.of("error", MALFORMED);
    }

    static String path(JacksonException ex) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : ex.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    static String expected(Class<?> type) {
        if (type == null) {
            return INVALID;
        }
        if (type.isEnum()) {
            return "Must be one of: " + Arrays.stream(type.getEnumConstants())
                    .map(constant -> ((Enum<?>) constant).name())
                    .collect(Collectors.joining(", "));
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class
                || type == Short.class || type == short.class || type == BigInteger.class) {
            return "Must be a whole number";
        }
        if (type == BigDecimal.class || type == Double.class || type == double.class
                || type == Float.class || type == float.class) {
            return "Must be a number";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "Must be true or false";
        }
        if (type == String.class) {
            return "Must be a string";
        }
        if (type == UUID.class) {
            return "Must be a valid UUID";
        }
        if (Temporal.class.isAssignableFrom(type)) {
            return "Must be a valid ISO-8601 date-time";
        }
        if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            return "Must be an array";
        }
        return INVALID;
    }
}
