package com.awesome.testing.controller.exception;

import com.awesome.testing.controller.cart.CartItemsController;
import com.awesome.testing.dto.ErrorDto;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller-scoped advice for cart item endpoints.
 */
@RestControllerAdvice(assignableTypes = {
        CartItemsController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CartExceptionHandler {

    static final String CART_CHANGED_CONCURRENTLY = "Cart was changed concurrently, please retry";

    /** Cart wording of the global concurrent-modification 409. */
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDto handleConcurrentCartChange(RuntimeException ex) {
        return new ErrorDto(CART_CHANGED_CONCURRENTLY);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDto handleProductNotFoundException(ProductNotFoundException ex) {
        return new ErrorDto(ex.getMessage());
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDto handleCartItemNotFoundException(CartItemNotFoundException ex) {
        return new ErrorDto(ex.getMessage());
    }
}
