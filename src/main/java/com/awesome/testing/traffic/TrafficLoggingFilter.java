package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.entity.TrafficLogEntity;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TrafficLoggingFilter implements Filter {

    private final Queue<TrafficEventDto> trafficQueue;
    private final TrafficLogService trafficLogService;
    private final TrafficDataSanitizer trafficDataSanitizer;
    private final TrafficProperties trafficProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        if (req instanceof HttpServletRequest httpReq && res instanceof HttpServletResponse httpRes) {
            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(
                    httpReq,
                    trafficProperties.getMaxBodyLength()
            );
            ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpRes);
            long start = System.currentTimeMillis();
            try {
                chain.doFilter(wrappedRequest, wrappedResponse);
            } finally {
                long duration = System.currentTimeMillis() - start;
                if (shouldCapture(wrappedRequest.getRequestURI())) {
                    Instant timestamp = Instant.now();
                    trafficQueue.add(TrafficEventDto.builder()
                            .method(wrappedRequest.getMethod())
                            .path(wrappedRequest.getRequestURI())
                            .status(wrappedResponse.getStatus())
                            .durationMs(duration)
                            .timestamp(timestamp)
                            .build());
                    trafficLogService.save(TrafficLogEntity.builder()
                            .correlationId(UUID.randomUUID().toString())
                            .timestamp(timestamp)
                            .durationMs(duration)
                            .method(wrappedRequest.getMethod())
                            .path(wrappedRequest.getRequestURI())
                            .queryString(wrappedRequest.getQueryString())
                            .status(wrappedResponse.getStatus())
                            .requestHeaders(serializeHeaders(extractHeaders(wrappedRequest)))
                            .requestBody(trafficDataSanitizer.sanitizeBody(readRequestBody(wrappedRequest)))
                            .responseHeaders(serializeHeaders(extractHeaders(wrappedResponse)))
                            .responseBody(trafficDataSanitizer.sanitizeBody(readResponseBody(wrappedResponse)))
                            .build());
                }
                wrappedResponse.copyBodyToResponse();
            }
        } else {
            chain.doFilter(req, res);
        }
    }

    private boolean shouldCapture(String path) {
        return trafficProperties.getExcludedPaths().stream().noneMatch(excluded ->
                path.equals(excluded) || path.startsWith(excluded.endsWith("/") ? excluded : excluded + "/"));
    }

    private Map<String, List<String>> extractHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return trafficDataSanitizer.sanitizeHeaders(headers);
    }

    private Map<String, List<String>> extractHeaders(HttpServletResponse response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, List.copyOf(response.getHeaders(name)));
        }
        return trafficDataSanitizer.sanitizeHeaders(headers);
    }

    private String serializeHeaders(Map<String, List<String>> headers) throws IOException {
        return objectMapper.writeValueAsString(headers);
    }

    private String readRequestBody(ContentCachingRequestWrapper request) {
        return readBody(request.getContentAsByteArray(), request.getCharacterEncoding());
    }

    private String readResponseBody(ContentCachingResponseWrapper response) {
        return readBody(response.getContentAsByteArray(), response.getCharacterEncoding());
    }

    private String readBody(byte[] body, String encoding) {
        if (body == null || body.length == 0) {
            return "";
        }
        Charset charset = encoding == null ? Charset.defaultCharset() : Charset.forName(encoding);
        return new String(body, charset);
    }
}
