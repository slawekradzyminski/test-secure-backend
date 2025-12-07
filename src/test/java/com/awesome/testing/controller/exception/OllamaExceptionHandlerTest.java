package com.awesome.testing.controller.exception;

import com.awesome.testing.controller.OllamaExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OllamaExceptionHandlerTest {

    private final OllamaExceptionHandler handler = new OllamaExceptionHandler();

    @Test
    void shouldHandleWebClientResponseException() {
        WebClientResponseException exception = WebClientResponseException.create(
                404,
                "Not Found",
                null,
                "{\"error\":\"model not found\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        ResponseEntity<String> response = handler.handleWebClientResponseException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).contains("model not found");
    }

    @Test
    void shouldHandleWebClientResponseExceptionWith500() {
        WebClientResponseException exception = WebClientResponseException.create(
                500,
                "Internal Server Error",
                null,
                "{\"error\":\"ollama server error\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        ResponseEntity<String> response = handler.handleWebClientResponseException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("ollama server error");
    }

    @Test
    void shouldHandleHttpMessageNotReadableException() {
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
        RuntimeException cause = new RuntimeException("Unexpected character");
        when(exception.getMostSpecificCause()).thenReturn(cause);

        ResponseEntity<OllamaExceptionHandler.ErrorResponse> response = 
                handler.handleMessageNotReadable(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("Invalid JSON format");
        assertThat(response.getBody().message()).contains("Unexpected character");
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}




