package com.awesome.testing.controller.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Message for a path or query parameter that cannot be converted to its declared type. */
final class ArgumentMismatchMessages {

    private static final int MAX_ECHOED_VALUE = 100;

    private ArgumentMismatchMessages() {
    }

    static String of(String parameter, Object value, Class<?> requiredType) {
        String rejected = abbreviate(String.valueOf(value));
        if (requiredType != null && requiredType.isEnum()) {
            String allowed = Arrays.stream(requiredType.getEnumConstants())
                    .map(constant -> ((Enum<?>) constant).name())
                    .collect(Collectors.joining(", "));
            return "Unknown " + parameter + " '" + rejected + "'. Allowed values: " + allowed;
        }
        String expected = UnreadableBodyMessages.expected(requiredType);
        if (UnreadableBodyMessages.INVALID.equals(expected)) {
            return "Invalid " + parameter + " '" + rejected + "'";
        }
        return "Invalid " + parameter + " '" + rejected + "': " + Character.toLowerCase(expected.charAt(0))
                + expected.substring(1);
    }

    private static String abbreviate(String value) {
        return value.length() <= MAX_ECHOED_VALUE ? value : value.substring(0, MAX_ECHOED_VALUE) + "...";
    }
}
