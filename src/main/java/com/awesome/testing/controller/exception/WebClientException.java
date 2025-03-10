package com.awesome.testing.controller.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class WebClientException extends RuntimeException {
    private final HttpStatusCode statusCode;

    public WebClientException(String message, HttpStatusCode statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

}

