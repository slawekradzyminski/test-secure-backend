package com.awesome.testing.controller.exception;

import com.awesome.testing.dto.ErrorDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartExceptionHandlerTest {

    private final CartExceptionHandler handler = new CartExceptionHandler();

    @Test
    void shouldHandleProductNotFound() {
        ErrorDto response = handler.handleProductNotFoundException(
                new ProductNotFoundException("Missing product"));

        assertThat(response.getMessage()).isEqualTo("Missing product");
    }

    @Test
    void shouldHandleCartItemNotFound() {
        ErrorDto response = handler.handleCartItemNotFoundException(
                new CartItemNotFoundException("Missing cart item"));

        assertThat(response.getMessage()).isEqualTo("Missing cart item");
    }
}
