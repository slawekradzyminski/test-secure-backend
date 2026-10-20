package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.entity.TrafficLogEntity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class HttpTrafficRecorder {
    private static final String CLIENT_SESSION_COOKIE = "clientSessionId";
    private static final List<String> STREAMING_PATH_PREFIXES =
            List.of("/api/v1/ollama/generate", "/api/v1/ollama/chat");
    private static final List<String> OMITTED_RESPONSE_MEDIA_TYPES =
            List.of(
                    MediaType.TEXT_EVENT_STREAM_VALUE,
                    MediaType.IMAGE_PNG_VALUE,
                    MediaType.APPLICATION_OCTET_STREAM_VALUE);

    private final Queue<TrafficEventDto> trafficQueue;
    private final TrafficLogService trafficLogService;
    private final TrafficDataSanitizer trafficDataSanitizer;
    private final ObjectMapper objectMapper;

    public void capture(
            ContentCachingRequestWrapper request,
            HttpServletResponse response,
            boolean captureResponseBody,
            long duration)
            throws IOException {
        Instant timestamp = Instant.now();
        String clientSessionId = resolveClientSessionId(request);
        String responseBody =
                captureResponseBody
                        ? readResponseBody((ContentCachingResponseWrapper) response)
                        : omittedBodyPlaceholder(response.getContentType());
        TrafficBodySanitizationResult requestBody = trafficDataSanitizer.sanitizeBody(readRequestBody(request));
        TrafficBodySanitizationResult sanitizedResponseBody = trafficDataSanitizer.sanitizeBody(responseBody);
        trafficQueue.offer(
                TrafficEventDto.builder()
                        .clientSessionId(clientSessionId)
                        .method(request.getMethod())
                        .path(request.getRequestURI())
                        .status(response.getStatus())
                        .durationMs(duration)
                        .timestamp(timestamp)
                        .build());
        trafficLogService.save(
                TrafficLogEntity.builder()
                        .correlationId(UUID.randomUUID().toString())
                        .clientSessionId(clientSessionId)
                        .timestamp(timestamp)
                        .durationMs(duration)
                        .method(request.getMethod())
                        .path(request.getRequestURI())
                        .queryString(request.getQueryString())
                        .status(response.getStatus())
                        .requestContentType(request.getContentType())
                        .requestBodyTruncated(requestBody.truncated())
                        .requestBodyOriginalLength(requestBody.originalLength())
                        .requestBodyStoredLength(requestBody.storedLength())
                        .requestHeaders(serializeHeaders(extractHeaders(request)))
                        .requestBody(requestBody.body())
                        .responseHeaders(serializeHeaders(extractHeaders(response)))
                        .responseContentType(response.getContentType())
                        .responseBodyTruncated(sanitizedResponseBody.truncated())
                        .responseBodyOriginalLength(sanitizedResponseBody.originalLength())
                        .responseBodyStoredLength(sanitizedResponseBody.storedLength())
                        .responseBody(sanitizedResponseBody.body())
                        .build());
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

    public boolean shouldCaptureResponseBody(HttpServletRequest request) {
        return STREAMING_PATH_PREFIXES.stream().noneMatch(request.getRequestURI()::startsWith)
                && !isOmittedMediaType(request.getHeader("Accept"));
    }

    private boolean isOmittedMediaType(String mediaTypeValue) {
        if (mediaTypeValue == null || mediaTypeValue.isBlank()) {
            return false;
        }
        return Stream.of(mediaTypeValue.split(","))
                .map(String::trim)
                .map(value -> value.split(";")[0].trim())
                .anyMatch(
                        candidate ->
                                OMITTED_RESPONSE_MEDIA_TYPES.stream()
                                        .anyMatch(omitted -> matchesMediaType(candidate, omitted)));
    }

    private boolean matchesMediaType(String candidate, String omitted) {
        if (candidate.equalsIgnoreCase(omitted)) {
            return true;
        }
        return omitted.startsWith("image/") && candidate.regionMatches(true, 0, "image/", 0, 6);
    }

    private String omittedBodyPlaceholder(String contentType) {
        String mediaType =
                contentType == null || contentType.isBlank()
                        ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                        : contentType.split(";")[0].trim();
        return "[omitted for media type " + mediaType + "]";
    }

    private String resolveClientSessionId(HttpServletRequest request) {
        String headerValue = TrafficSession.validOrNull(request.getHeader(TrafficSession.HEADER));
        if (headerValue != null) {
            return headerValue;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CLIENT_SESSION_COOKIE.equals(cookie.getName())) {
                return TrafficSession.validOrNull(cookie.getValue());
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
