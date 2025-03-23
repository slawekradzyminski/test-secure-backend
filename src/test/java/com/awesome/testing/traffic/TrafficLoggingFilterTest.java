package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ConcurrentLinkedQueue;

import static com.awesome.testing.factory.ollama.TrafficEventFactory.trafficEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TrafficLoggingFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private ConcurrentLinkedQueue<TrafficEventDto> trafficQueue;
    private TrafficLoggingFilter trafficLoggingFilter;
    private TrafficEventDto trafficEvent;

    @BeforeEach
    void setUp() {
        // given
        trafficQueue = new ConcurrentLinkedQueue<>();
        trafficLoggingFilter = new TrafficLoggingFilter(trafficQueue);
        trafficEvent = trafficEvent();

        when(request.getMethod()).thenReturn(trafficEvent.getMethod());
        when(request.getRequestURI()).thenReturn(trafficEvent.getPath());
        when(response.getStatus()).thenReturn(trafficEvent.getStatus());
    }

    @Test
    void shouldAddEventToQueueWhenHttpRequestIsProcessed() throws Exception {
        // when
        trafficLoggingFilter.doFilter(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response);
        assertThat(trafficQueue).isNotEmpty();
        TrafficEventDto event = trafficQueue.poll();
        assertThat(event.getMethod()).isEqualTo(trafficEvent.getMethod());
        assertThat(event.getPath()).isEqualTo(trafficEvent.getPath());
        assertThat(event.getStatus()).isEqualTo(trafficEvent.getStatus());
    }
} 