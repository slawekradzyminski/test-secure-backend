package com.awesome.testing.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public class CustomException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    final String message;
    final HttpStatus httpStatus;

}
