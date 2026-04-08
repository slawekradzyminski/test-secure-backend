package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrafficLoggingFilterTest {

    private ConcurrentLinkedQueue<TrafficEventDto> queue;
    private TrafficLoggingFilter filter;
    private FilterChain chain;
    private TrafficLogService trafficLogService;

    @BeforeEach
    void setUp() {
        queue = new ConcurrentLinkedQueue<>();
        chain = mock(FilterChain.class);
        trafficLogService = mock(TrafficLogService.class);
        filter = new TrafficLoggingFilter(
                queue,
                trafficLogService,
                new TrafficDataSanitizer(new TrafficProperties()),
                new TrafficProperties(),
                new ObjectMapper()
        );
    }

    @Test
    void shouldCaptureHttpRequests() throws IOException, ServletException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of()));
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(response.getStatus()).thenReturn(200);
        when(response.getHeaderNames()).thenReturn(List.of());
        when(response.getCharacterEncoding()).thenReturn("UTF-8");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(queue).hasSize(1);
        TrafficEventDto event = queue.poll();
        assertThat(event.getMethod()).isEqualTo("GET");
        assertThat(event.getPath()).isEqualTo("/api/test");
        assertThat(event.getStatus()).isEqualTo(200);
        assertThat(event.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void shouldSkipExcludedTrafficEndpoints() throws IOException, ServletException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/traffic/logs");
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of()));
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(response.getStatus()).thenReturn(200);
        when(response.getHeaderNames()).thenReturn(List.of());
        when(response.getCharacterEncoding()).thenReturn("UTF-8");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(queue).isEmpty();
        verifyNoInteractions(trafficLogService);
    }

    @Test
    void shouldSkipNonHttpRequests() throws IOException, ServletException {
        ServletRequest request = mock(ServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(queue).isEmpty();
    }
}
