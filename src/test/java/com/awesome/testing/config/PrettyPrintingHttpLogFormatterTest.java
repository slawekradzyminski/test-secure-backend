package com.awesome.testing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.logbook.*;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrettyPrintingHttpLogFormatterTest {

    private PrettyPrintingHttpLogFormatter formatter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        formatter = new PrettyPrintingHttpLogFormatter(objectMapper);
    }

    @Test
    void shouldFormatRequestWithJsonBody() throws IOException {
        HttpRequest request = mock(HttpRequest.class);
        Precorrelation precorrelation = mock(Precorrelation.class);

        when(request.getBodyAsString()).thenReturn("{\"key\":\"value\"}");
        when(request.getProtocolVersion()).thenReturn("HTTP/1.1");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestUri()).thenReturn("/api/test");
        when(request.getHeaders()).thenReturn(HttpHeaders.of("Content-Type", "application/json"));
        when(precorrelation.getId()).thenReturn("test-correlation-id");

        String result = formatter.format(precorrelation, request);

        assertThat(result).contains("\"type\":\"request\"");
        assertThat(result).contains("\"method\":\"POST\"");
        assertThat(result).contains("\"uri\":\"/api/test\"");
        assertThat(result).contains("\"correlation\":\"test-correlation-id\"");
    }

    @Test
    void shouldFormatRequestWithNonJsonBody() throws IOException {
        HttpRequest request = mock(HttpRequest.class);
        Precorrelation precorrelation = mock(Precorrelation.class);

        when(request.getBodyAsString()).thenReturn("plain text body");
        when(request.getProtocolVersion()).thenReturn("HTTP/1.1");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestUri()).thenReturn("/api/test");
        when(request.getHeaders()).thenReturn(HttpHeaders.empty());
        when(precorrelation.getId()).thenReturn("test-id");

        String result = formatter.format(precorrelation, request);

        assertThat(result).contains("\"body\":\"plain text body\"");
    }

    @Test
    void shouldFormatResponseWithJsonBody() throws IOException {
        HttpResponse response = mock(HttpResponse.class);
        Correlation correlation = mock(Correlation.class);

        when(response.getBodyAsString()).thenReturn("{\"result\":\"success\"}");
        when(response.getProtocolVersion()).thenReturn("HTTP/1.1");
        when(response.getStatus()).thenReturn(200);
        when(response.getHeaders()).thenReturn(HttpHeaders.of("Content-Type", "application/json"));
        when(correlation.getId()).thenReturn("response-correlation-id");
        when(correlation.getDuration()).thenReturn(Duration.ofMillis(150));

        String result = formatter.format(correlation, response);

        assertThat(result).contains("\"type\":\"response\"");
        assertThat(result).contains("\"status\":200");
        assertThat(result).contains("\"duration\":150");
        assertThat(result).contains("\"correlation\":\"response-correlation-id\"");
    }

    @Test
    void shouldFormatResponseWithNonJsonBody() throws IOException {
        HttpResponse response = mock(HttpResponse.class);
        Correlation correlation = mock(Correlation.class);

        when(response.getBodyAsString()).thenReturn("error message");
        when(response.getProtocolVersion()).thenReturn("HTTP/1.1");
        when(response.getStatus()).thenReturn(500);
        when(response.getHeaders()).thenReturn(HttpHeaders.empty());
        when(correlation.getId()).thenReturn("error-id");
        when(correlation.getDuration()).thenReturn(Duration.ofMillis(50));

        String result = formatter.format(correlation, response);

        assertThat(result).contains("\"body\":\"error message\"");
        assertThat(result).contains("\"status\":500");
    }

    @Test
    void shouldHandleEmptyBody() throws IOException {
        HttpRequest request = mock(HttpRequest.class);
        Precorrelation precorrelation = mock(Precorrelation.class);

        when(request.getBodyAsString()).thenReturn("");
        when(request.getProtocolVersion()).thenReturn("HTTP/1.1");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestUri()).thenReturn("/api/health");
        when(request.getHeaders()).thenReturn(HttpHeaders.empty());
        when(precorrelation.getId()).thenReturn("empty-body-id");

        String result = formatter.format(precorrelation, request);

        assertThat(result).contains("\"body\":\"\"");
    }
}




