package com.awesome.testing.controller.exception;

import com.awesome.testing.dto.ErrorDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductExceptionHandlerTest {

    private final ProductExceptionHandler handler = new ProductExceptionHandler();

    @Test
    void shouldHandleProductNotFound() {
        ErrorDto response = handler.handleProductNotFoundException(
                new ProductNotFoundException("Missing product"));

        assertThat(response.getMessage()).isEqualTo("Missing product");
    }
}
