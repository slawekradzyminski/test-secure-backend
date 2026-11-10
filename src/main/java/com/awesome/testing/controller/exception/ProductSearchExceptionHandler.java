package com.awesome.testing.controller.exception;

import com.awesome.testing.controller.ProductSearchController;
import com.awesome.testing.service.search.ProductSearchValidationException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Renders invalid product search parameters as a 400 field-error map ({@code ValidationErrorsDto}).
 * The controller binds every scalar parameter as a string and {@code ProductSearchCriteria} parses it, so
 * type errors are keyed by parameter name too instead of reaching the global IllegalArgumentException
 * mapping.
 */
@RestControllerAdvice(assignableTypes = ProductSearchController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductSearchExceptionHandler {

    @ExceptionHandler(ProductSearchValidationException.class)
    public ResponseEntity<Map<String, String>> handleInvalidSearch(ProductSearchValidationException ex) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(ex.getErrors());
    }
}
