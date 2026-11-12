package com.awesome.testing.controller.exception;

import com.awesome.testing.controller.QrController;
import com.awesome.testing.service.QrTextTooLongException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Renders text that does not fit in a QR code as a 400 field error on {@code text} ({@code ValidationErrorsDto}). */
@RestControllerAdvice(assignableTypes = QrController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QrExceptionHandler {

    @ExceptionHandler(QrTextTooLongException.class)
    public ResponseEntity<Map<String, String>> handleTextTooLong(QrTextTooLongException ex) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(Map.of("text", ex.getMessage()));
    }
}
