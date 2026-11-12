package com.awesome.testing.controller.exception;

import com.awesome.testing.controller.giftcard.AdminGiftCardController;
import com.awesome.testing.dto.ErrorDto;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Renders unparseable admin gift card query parameters (for example {@code ?status=LOST}) as the documented
 * 400 {@link ErrorDto} naming the parameter and the rejected value. Without it the global
 * IllegalArgumentException mapping answers with {@code {"error": "No enum constant ..."}}, which leaks the
 * Java class name. Scoped to the gift card admin controller because older endpoints (for example
 * {@code GET /api/v1/orders?status=}) keep their existing course-covered body.
 */
@RestControllerAdvice(assignableTypes = AdminGiftCardController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GiftCardExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDto handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return new ErrorDto(message(ex.getName(), ex.getValue(), ex.getRequiredType()));
    }

    static String message(String parameter, Object value, Class<?> requiredType) {
        if (requiredType != null && requiredType.isEnum()) {
            String allowed = Arrays.stream(requiredType.getEnumConstants())
                    .map(constant -> ((Enum<?>) constant).name())
                    .collect(Collectors.joining(", "));
            return "Unknown " + parameter + " '" + value + "'. Allowed values: " + allowed;
        }
        return "Invalid " + parameter + " '" + value + "'";
    }
}
