package com.awesome.testing.controller.exception;

import com.awesome.testing.controller.EmbeddingsController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice(assignableTypes = {
        EmbeddingsController.class
})
@Slf4j
public class WebClientExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleWebClientResponseException(WebClientResponseException ex) {
        log.error("WebClient response error: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", ex.getStatusCode().value());
        response.put("error", ex.getStatusCode().toString());
        response.put("message", "Error from sidecar service: " + ex.getMessage());
        response.put("path", ex.getRequest().getURI().toString());
        
        return Mono.just(ResponseEntity.status(ex.getStatusCode()).body(response));
    }
    
    @ExceptionHandler(WebClientRequestException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleWebClientRequestException(WebClientRequestException ex) {
        log.error("WebClient request error: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", HttpStatus.SERVICE_UNAVAILABLE.toString());
        response.put("message", "Error connecting to sidecar service: " + ex.getMessage());
        
        String path = "unknown";
        try {
            if (ex.getMethod() != null && ex.getUri() != null) {
                path = ex.getUri().toString();
            }
        } catch (Exception e) {
            log.warn("Could not extract URI from WebClientRequestException", e);
        }
        response.put("path", path);
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
    
    @ExceptionHandler(TimeoutException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleTimeoutException(TimeoutException ex) {
        log.error("Timeout error: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.GATEWAY_TIMEOUT.value());
        response.put("error", HttpStatus.GATEWAY_TIMEOUT.toString());
        response.put("message", "Request timed out: " + ex.getMessage());
        
        return Mono.just(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response));
    }
} 