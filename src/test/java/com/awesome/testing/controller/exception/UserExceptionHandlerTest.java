package com.awesome.testing.controller.exception;

import com.awesome.testing.dto.ErrorDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserExceptionHandlerTest {

    private final UserExceptionHandler handler = new UserExceptionHandler();

    @Test
    void shouldHandleUserNotFound() {
        ErrorDto response = handler.handleUserNotFoundException(
                new UserNotFoundException("No user"));

        assertThat(response.getMessage()).isEqualTo("No user");
    }
}
