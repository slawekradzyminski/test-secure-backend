package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrafficLoggingFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private ConcurrentLinkedQueue<TrafficEventDto> trafficQueue;
    private TrafficLoggingFilter trafficLoggingFilter;

    @BeforeEach
    void setUp() {
        // given
        MockitoAnnotations.openMocks(this);
        trafficQueue = new ConcurrentLinkedQueue<>();
        trafficLoggingFilter = new TrafficLoggingFilter(trafficQueue);
        
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getStatus()).thenReturn(200);
    }

    @Test
    void shouldAddEventToQueueWhenHttpRequestIsProcessed() throws Exception {
        // when
        trafficLoggingFilter.doFilter(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response);
        assertFalse(trafficQueue.isEmpty());
        
        TrafficEventDto event = trafficQueue.poll();
        assertEquals("GET", event.getMethod());
        assertEquals("/api/test", event.getPath());
        assertEquals(200, event.getStatus());
    }
} 