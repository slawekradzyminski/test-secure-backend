package com.awesome.testing.traffic;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class TrafficLoggingFilter implements Filter {
    private final TrafficProperties properties;
    private final TrafficCapturePolicy policy;
    private final HttpTrafficRecorder recorder;
    private final TrafficTimer timer;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (!(req instanceof HttpServletRequest request)
                || !(res instanceof HttpServletResponse response)
                || "/api/v1/graphql".equals(request.getServletPath())) {
            chain.doFilter(req, res);
            return;
        }
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request, properties.getMaxBodyLength());
        boolean captureBody = recorder.shouldCaptureResponseBody(request);
        HttpServletResponse target = captureBody ? new ContentCachingResponseWrapper(response) : response;
        long start = timer.start();
        try {
            chain.doFilter(wrapped, target);
        } finally {
            recordSafely(wrapped, target, captureBody, timer.elapsedMillis(start));
        }
    }

    private void recordSafely(
            ContentCachingRequestWrapper request, HttpServletResponse response, boolean captureBody, long duration)
            throws IOException {
        try {
            if (policy.shouldCapture(request)) {
                recorder.capture(request, response, captureBody, duration);
            }
        } catch (RuntimeException | IOException exception) {
            log.warn("HTTP traffic capture failed");
        } finally {
            if (response instanceof ContentCachingResponseWrapper wrapped) {
                wrapped.copyBodyToResponse();
            }
        }
    }
}
