package com.awesome.testing.traffic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.awesome.testing.entity.TrafficLogEntity;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;

class HttpTrafficRecorderTest {
    private final TrafficLogService logs = mock(TrafficLogService.class);
    private final HttpTrafficRecorder recorder =
            new HttpTrafficRecorder(
                    new ConcurrentLinkedQueue<>(),
                    logs,
                    new TrafficDataSanitizer(new TrafficProperties()),
                    new ObjectMapper());

    @Test
    void capturesActualUtf8BodiesAndBothHeaderMaps() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/test");
        request.setCharacterEncoding("UTF-8");
        request.setContentType("application/json");
        request.addHeader("X-Request", "business-request");
        request.addHeader(TrafficSession.HEADER, "header-session-1234");
        request.setCookies(new Cookie("clientSessionId", "cookie-session-1234"));
        String input = "{\"note\":\"Zażółć\"}";
        request.setContent(input.getBytes(StandardCharsets.UTF_8));
        var cached = new ContentCachingRequestWrapper(request, 4000);
        cached.getInputStream().readAllBytes();
        var response = new ContentCachingResponseWrapper(new MockHttpServletResponse());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("X-Response", "business-response");
        response.getWriter().write(input);
        response.getWriter().flush();

        recorder.capture(cached, response, true, 12);

        var record = captured();
        assertThat(record.getRequestBody()).isEqualTo(input);
        assertThat(record.getResponseBody()).isEqualTo(input);
        assertThat(record.getRequestHeaders()).contains("business-request");
        assertThat(record.getResponseHeaders()).contains("business-response");
        assertThat(record.getClientSessionId()).isEqualTo("header-session-1234");
        assertThat(record.getDurationMs()).isEqualTo(12);
    }

    @Test
    void legacyCookieCanSupplyAValidSessionAfterAnInvalidHeader() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(TrafficSession.HEADER, "invalid");
        request.setCookies(new Cookie("other", "ignored"), new Cookie("clientSessionId", "cookie-session-1234"));
        var response = new MockHttpServletResponse();
        response.setContentType("image/jpeg");

        recorder.capture(new ContentCachingRequestWrapper(request, 4000), response, false, 1);

        var record = captured();
        assertThat(record.getClientSessionId()).isEqualTo("cookie-session-1234");
        assertThat(record.getResponseBody()).isEqualTo("[omitted for media type image/jpeg]");
    }

    @Test
    void honorsTheDeclaredCharsetForTextBodies() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/test");
        request.setCharacterEncoding("ISO-8859-1");
        request.setContent("café".getBytes(StandardCharsets.ISO_8859_1));
        var cached = new ContentCachingRequestWrapper(request, 4000);
        cached.getInputStream().readAllBytes();
        var response = new ContentCachingResponseWrapper(new MockHttpServletResponse());
        response.setCharacterEncoding("ISO-8859-1");
        response.getWriter().write("café");
        response.getWriter().flush();

        recorder.capture(cached, response, true, 1);

        var record = captured();
        assertThat(record.getRequestBody()).isEqualTo("café");
        assertThat(record.getResponseBody()).isEqualTo("café");
    }

    @Test
    void unrelatedCookiesDoNotBecomeATrafficSession() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/test");
        request.setCookies(new Cookie("other", "unrelated-session-1234"));

        recorder.capture(new ContentCachingRequestWrapper(request, 4000), new MockHttpServletResponse(), false, 1);

        assertThat(captured().getClientSessionId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/plain", "application/json, text/html", ""})
    void capturesOrdinaryResponseMediaTypes(String accept) {
        var request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("Accept", accept);
        assertThat(recorder.shouldCaptureResponseBody(request)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "image/png",
                "image/jpeg; q=0.9",
                "IMAGE/JPEG",
                "image/*",
                "text/event-stream",
                "application/octet-stream"
            })
    void omitsBinaryAndStreamingResponseMediaTypes(String accept) {
        var request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("Accept", accept);
        assertThat(recorder.shouldCaptureResponseBody(request)).isFalse();
    }

    @Test
    void aMissingAcceptHeaderStillCapturesNormalResponsesButNeverStreamingRoutes() {
        assertThat(recorder.shouldCaptureResponseBody(new MockHttpServletRequest("GET", "/api/test"))).isTrue();
        assertThat(recorder.shouldCaptureResponseBody(new MockHttpServletRequest("POST", "/api/v1/ollama/chat")))
                .isFalse();
    }

    private TrafficLogEntity captured() {
        var captor = ArgumentCaptor.forClass(TrafficLogEntity.class);
        verify(logs).save(captor.capture());
        return captor.getValue();
    }
}
