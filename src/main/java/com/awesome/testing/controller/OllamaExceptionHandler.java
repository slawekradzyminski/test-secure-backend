package com.awesome.testing.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.time.Instant;

@RestControllerAdvice
public class OllamaExceptionHandler {
    
    public record ErrorResponse(
        int status,
        String message,
        String timestamp
    ) {}
    
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientResponseException(WebClientResponseException ex) {
        return ResponseEntity
            .status(ex.getStatusCode())
            .body(new ErrorResponse(
                ex.getStatusCode().value(),
                ex.getMessage(),
                Instant.now().toString()
            ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse(
                400,
                "Invalid JSON format: " + ex.getMostSpecificCause().getMessage(),
                Instant.now().toString()
            ));
    }
} 