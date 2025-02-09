package com.awesome.testing.controller.exception;

import com.awesome.testing.controller.users.*;
import com.awesome.testing.dto.ErrorDto;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@RestControllerAdvice(assignableTypes = {
        UserDeleteController.class,
        UserEditController.class,
        UserGetSingleUserController.class,
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDto handleUserNotFoundException(UserNotFoundException ex) {
        return new ErrorDto(ex.getMessage());
    }
}