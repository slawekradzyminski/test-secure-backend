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

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrafficLoggingFilterTest {

    private ConcurrentLinkedQueue<TrafficEventDto> queue;
    private TrafficLoggingFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        queue = new ConcurrentLinkedQueue<>();
        filter = new TrafficLoggingFilter(queue);
        chain = mock(FilterChain.class);
    }

    @Test
    void shouldCaptureHttpRequests() throws IOException, ServletException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(queue).hasSize(1);
        TrafficEventDto event = queue.poll();
        assertThat(event.getMethod()).isEqualTo("GET");
        assertThat(event.getPath()).isEqualTo("/api/test");
        assertThat(event.getStatus()).isEqualTo(200);
        assertThat(event.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(event.getTimestamp()).isNotNull();
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
