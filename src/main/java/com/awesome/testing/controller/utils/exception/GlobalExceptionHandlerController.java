package com.awesome.testing.controller.utils.exception;

import java.util.HashMap;
import java.util.Map;

import com.awesome.testing.exception.ApiException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class GlobalExceptionHandlerController {

    @ExceptionHandler(ApiException.class)
    protected ResponseEntity<Object> handleCustomException(ApiException ex, WebRequest request) {
        Map<String, String> bodyOfResponse = new HashMap<>();
        bodyOfResponse.put("message", ex.getMessage());
        return new ResponseEntity<>(bodyOfResponse, new HttpHeaders(), ex.getHttpStatus());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    protected ResponseEntity<Object> handleEntityNotFoundException(EntityNotFoundException ex, WebRequest request) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        Map<String, String> bodyOfResponse = new HashMap<>();
        bodyOfResponse.put("message", "Access denied");
        return new ResponseEntity<>(bodyOfResponse, new HttpHeaders(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex,
            WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            if (error instanceof FieldError) {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            } else {
                if (error.getDefaultMessage() != null) {
                    errors.put("message", error.getDefaultMessage());
                }
            }
        });
        return new ResponseEntity<>(errors, new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }
}