package com.awesome.testing.controller.exception;

import com.awesome.testing.controller.ProductController;
import com.awesome.testing.dto.ErrorDto;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller-scoped advice for product catalog endpoints.
 */
@RestControllerAdvice(assignableTypes = ProductController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDto handleProductNotFoundException(ProductNotFoundException ex) {
        return new ErrorDto(ex.getMessage());
    }
}
