package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.entity.TrafficLogEntity;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
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
import java.util.stream.Stream;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TrafficLoggingFilter implements Filter {

    private static final String CLIENT_SESSION_HEADER = "X-Client-Session-Id";
    private static final String CLIENT_SESSION_COOKIE = "clientSessionId";
    private static final List<String> OMITTED_RESPONSE_MEDIA_TYPES = List.of(
            MediaType.TEXT_EVENT_STREAM_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            MediaType.APPLICATION_OCTET_STREAM_VALUE
    );

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
            boolean captureResponseBody = shouldCaptureResponseBody(httpReq);
            HttpServletResponse responseToUse = captureResponseBody
                    ? new ContentCachingResponseWrapper(httpRes)
                    : httpRes;
            long start = System.nanoTime();
            try {
                chain.doFilter(wrappedRequest, responseToUse);
            } finally {
                long duration = (System.nanoTime() - start) / 1_000_000;
                if (shouldCapture(wrappedRequest.getRequestURI())) {
                    Instant timestamp = Instant.now();
                    String responseBody = captureResponseBody
                            ? readResponseBody((ContentCachingResponseWrapper) responseToUse)
                            : omittedBodyPlaceholder(responseToUse.getContentType());
                    trafficQueue.add(TrafficEventDto.builder()
                            .method(wrappedRequest.getMethod())
                            .path(wrappedRequest.getRequestURI())
                            .status(responseToUse.getStatus())
                            .durationMs(duration)
                            .timestamp(timestamp)
                            .build());
                    trafficLogService.save(TrafficLogEntity.builder()
                            .correlationId(UUID.randomUUID().toString())
                            .clientSessionId(resolveClientSessionId(wrappedRequest))
                            .timestamp(timestamp)
                            .durationMs(duration)
                            .method(wrappedRequest.getMethod())
                            .path(wrappedRequest.getRequestURI())
                            .queryString(wrappedRequest.getQueryString())
                            .status(responseToUse.getStatus())
                            .requestHeaders(serializeHeaders(extractHeaders(wrappedRequest)))
                            .requestBody(trafficDataSanitizer.sanitizeBody(readRequestBody(wrappedRequest)))
                            .responseHeaders(serializeHeaders(extractHeaders(responseToUse)))
                            .responseBody(trafficDataSanitizer.sanitizeBody(responseBody))
                            .build());
                }
                if (responseToUse instanceof ContentCachingResponseWrapper wrappedResponse) {
                    wrappedResponse.copyBodyToResponse();
                }
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

    private boolean shouldCaptureResponseBody(HttpServletRequest request) {
        return !isOmittedMediaType(request.getHeader("Accept"));
    }

    private boolean isOmittedMediaType(String mediaTypeValue) {
        if (mediaTypeValue == null || mediaTypeValue.isBlank()) {
            return false;
        }
        return Stream.of(mediaTypeValue.split(","))
                .map(String::trim)
                .map(value -> value.split(";")[0].trim())
                .anyMatch(candidate -> OMITTED_RESPONSE_MEDIA_TYPES.stream()
                        .anyMatch(omitted -> matchesMediaType(candidate, omitted)));
    }

    private boolean matchesMediaType(String candidate, String omitted) {
        if (candidate.equalsIgnoreCase(omitted)) {
            return true;
        }
        return omitted.startsWith("image/") && candidate.startsWith("image/");
    }

    private String omittedBodyPlaceholder(String contentType) {
        String mediaType = contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : contentType.split(";")[0].trim();
        return "[omitted for media type " + mediaType + "]";
    }

    private String resolveClientSessionId(HttpServletRequest request) {
        String headerValue = request.getHeader(CLIENT_SESSION_HEADER);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CLIENT_SESSION_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
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
