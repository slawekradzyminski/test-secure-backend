package com.awesome.testing.service.search;

import java.util.Map;

/** Invalid search parameters, keyed by request parameter name; rendered as a 400 {@code ValidationErrorsDto}. */
public class ProductSearchValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> errors;

    public ProductSearchValidationException(Map<String, String> errors) {
        super("Invalid product search parameters: " + errors.keySet());
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
