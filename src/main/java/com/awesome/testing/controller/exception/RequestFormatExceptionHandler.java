package com.awesome.testing.controller.exception;

import com.awesome.testing.dto.ErrorDto;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Request bodies and media types the API cannot read, answered in the API's own error shapes instead
 * of Spring's default {timestamp, status, error, path} body. Unreadable JSON is a 400 validation body:
 * a field error when Jackson knows which field was wrong, otherwise one {@code error} entry; parser
 * text and Java type names are never echoed. Ordered last so controller-scoped advice (the Ollama
 * proxy keeps its own unreadable-body format) still wins.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestFormatExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity
                .badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(UnreadableBodyMessages.forException(ex));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorDto> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        String contentType = ex.getContentType() == null ? "(none)"
                : ex.getContentType().getType() + "/" + ex.getContentType().getSubtype();
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorDto("Content type " + contentType + " is not supported"));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorDto> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorDto("The requested response media type is not available"));
    }
}
