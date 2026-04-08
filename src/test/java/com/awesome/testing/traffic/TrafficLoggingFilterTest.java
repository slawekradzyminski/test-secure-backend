package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.entity.TrafficLogEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrafficLoggingFilterTest {

    private ConcurrentLinkedQueue<TrafficEventDto> queue;
    private TrafficLoggingFilter filter;
    private FilterChain chain;
    private TrafficLogService trafficLogService;
    private TrafficCapturePolicy trafficCapturePolicy;

    @BeforeEach
    void setUp() {
        queue = new ConcurrentLinkedQueue<>();
        chain = mock(FilterChain.class);
        trafficLogService = mock(TrafficLogService.class);
        trafficCapturePolicy = new TrafficCapturePolicy(new TrafficProperties());
        filter = new TrafficLoggingFilter(
                queue,
                trafficLogService,
                new TrafficDataSanitizer(new TrafficProperties()),
                new TrafficProperties(),
                trafficCapturePolicy,
                new ObjectMapper()
        );
    }

    @Test
    void shouldCaptureHttpRequests() throws IOException, ServletException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("X-Client-Session-Id")).thenReturn("client-123");
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of()));
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE))
                .thenReturn(handlerMethod(TestController.class, "documentedEndpoint"));
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

        ArgumentCaptor<TrafficLogEntity> entityCaptor = ArgumentCaptor.forClass(TrafficLogEntity.class);
        verify(trafficLogService).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getClientSessionId()).isEqualTo("client-123");
    }

    @Test
    void shouldSkipExcludedTrafficEndpoints() throws IOException, ServletException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/traffic/logs");
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of()));
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE))
                .thenReturn(handlerMethod(TestController.class, "documentedEndpoint"));
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

    @Test
    void shouldSkipResponseBodyCaptureForStreamingRequests() throws IOException, ServletException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/ollama/chat");
        when(request.getHeader("Accept")).thenReturn(MediaType.TEXT_EVENT_STREAM_VALUE);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of("Accept")));
        when(request.getHeaders("Accept")).thenReturn(Collections.enumeration(List.of(MediaType.TEXT_EVENT_STREAM_VALUE)));
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE))
                .thenReturn(handlerMethod(TestController.class, "documentedEndpoint"));
        when(response.getStatus()).thenReturn(200);
        when(response.getContentType()).thenReturn(MediaType.TEXT_EVENT_STREAM_VALUE);
        when(response.getHeaderNames()).thenReturn(List.of("Content-Type"));
        when(response.getHeaders("Content-Type")).thenReturn(List.of(MediaType.TEXT_EVENT_STREAM_VALUE));

        filter.doFilter(request, response, chain);

        ArgumentCaptor<TrafficLogEntity> entityCaptor = ArgumentCaptor.forClass(TrafficLogEntity.class);
        verify(trafficLogService).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getResponseBody()).isEqualTo("[omitted for media type text/event-stream]");
    }

    @Test
    void shouldSkipResponseBodyCaptureForStreamingPathsWithoutAcceptHeader() throws IOException, ServletException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/ollama/generate");
        when(request.getHeader("Accept")).thenReturn(null);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of()));
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE))
                .thenReturn(handlerMethod(TestController.class, "documentedEndpoint"));
        when(response.getStatus()).thenReturn(200);
        when(response.getContentType()).thenReturn(MediaType.TEXT_EVENT_STREAM_VALUE);
        when(response.getHeaderNames()).thenReturn(List.of("Content-Type"));
        when(response.getHeaders("Content-Type")).thenReturn(List.of(MediaType.TEXT_EVENT_STREAM_VALUE));

        filter.doFilter(request, response, chain);

        ArgumentCaptor<TrafficLogEntity> entityCaptor = ArgumentCaptor.forClass(TrafficLogEntity.class);
        verify(trafficLogService).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getResponseBody()).isEqualTo("[omitted for media type text/event-stream]");
    }

    @Test
    void shouldSkipResponseBodyCaptureForBinaryResponses() throws IOException, ServletException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/qr/create");
        when(request.getHeader("Accept")).thenReturn(MediaType.IMAGE_PNG_VALUE);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of("Accept")));
        when(request.getHeaders("Accept")).thenReturn(Collections.enumeration(List.of(MediaType.IMAGE_PNG_VALUE)));
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE))
                .thenReturn(handlerMethod(TestController.class, "documentedEndpoint"));
        when(response.getStatus()).thenReturn(200);
        when(response.getContentType()).thenReturn(MediaType.IMAGE_PNG_VALUE);
        when(response.getHeaderNames()).thenReturn(List.of("Content-Type"));
        when(response.getHeaders("Content-Type")).thenReturn(List.of(MediaType.IMAGE_PNG_VALUE));

        filter.doFilter(request, response, chain);

        ArgumentCaptor<TrafficLogEntity> entityCaptor = ArgumentCaptor.forClass(TrafficLogEntity.class);
        verify(trafficLogService).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getResponseBody()).isEqualTo("[omitted for media type image/png]");
    }

    @Test
    void shouldSkipRequestsWithoutDocumentedHandler() throws IOException, ServletException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/actuator/health");
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of()));
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE))
                .thenReturn(handlerMethod(UndocumentedController.class, "undocumentedEndpoint"));
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, chain);

        assertThat(queue).isEmpty();
        verifyNoInteractions(trafficLogService);
    }

    private HandlerMethod handlerMethod(Class<?> controllerType, String methodName) {
        try {
            return new HandlerMethod(controllerType.getDeclaredConstructor().newInstance(),
                    controllerType.getDeclaredMethod(methodName));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @RestController
    @RequestMapping("/api/test")
    static class TestController {

        @io.swagger.v3.oas.annotations.Operation(summary = "Documented")
        @GetMapping
        void documentedEndpoint() {
        }
    }

    @RestController
    @RequestMapping("/actuator")
    static class UndocumentedController {

        @GetMapping("/health")
        void undocumentedEndpoint() {
        }
    }
}
