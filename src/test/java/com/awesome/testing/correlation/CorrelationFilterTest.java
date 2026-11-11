package com.awesome.testing.correlation;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationFilterTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PARENT_ID = "00f067aa0ba902b7";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + PARENT_ID + "-01";

    private final CorrelationFilter filter = new CorrelationFilter();
    private final Map<String, String> seen = new HashMap<>();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsValidIncomingIdsInTheMdcAndReturnsTheRequestId() throws Exception {
        // given
        MockHttpServletRequest request = request("smoke-123", TRACEPARENT);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, capturingChain());

        // then
        assertThat(seen).containsEntry("requestId", "smoke-123").containsEntry("traceId", TRACE_ID);
        assertThat(seen.get("spanId")).matches("[0-9a-f]{16}").isNotEqualTo(PARENT_ID);
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("smoke-123");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void generatesIdsWhenTheHeadersAreMissing() throws Exception {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request(null, null), response, capturingChain());

        // then
        assertThat(seen.get("requestId")).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(seen.get("traceId")).matches("[0-9a-f]{32}");
        assertThat(seen.get("spanId")).matches("[0-9a-f]{16}");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(seen.get("requestId"));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void replacesInvalidHeaders() throws Exception {
        // given
        String tooLong = "x".repeat(129);
        MockHttpServletRequest request = request(tooLong, "00-" + "0".repeat(32) + "-" + PARENT_ID + "-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, capturingChain());

        // then
        assertThat(seen.get("requestId")).isNotEqualTo(tooLong).hasSize(36);
        assertThat(seen.get("traceId")).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(seen.get("requestId"));
    }

    @Test
    void replacesARequestIdWithForbiddenCharacters() throws Exception {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request("evil\r\nSet-Cookie: x", TRACEPARENT), response, capturingChain());

        // then
        assertThat(response.getHeader("X-Request-Id")).hasSize(36).doesNotContain("evil");
        assertThat(seen.get("traceId")).isEqualTo(TRACE_ID);
    }

    @Test
    void clearsTheMdcWhenTheChainFails() {
        // given
        MockFilterChain failing = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws ServletException {
                throw new ServletException("boom");
            }
        };

        // when / then
        assertThatThrownBy(() -> filter.doFilter(request("req", null), new MockHttpServletResponse(), failing))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void restoresAnOuterMdcAfterTheRequest() throws Exception {
        // given
        MDC.put("requestId", "outer");

        // when
        filter.doFilter(request("inner", null), new MockHttpServletResponse(), capturingChain());

        // then
        assertThat(seen).containsEntry("requestId", "inner");
        assertThat(MDC.get("requestId")).isEqualTo("outer");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void asyncAndErrorDispatchesReuseTheIdsOfTheRequest() throws Exception {
        // given
        MockHttpServletRequest request = request(null, null);
        filter.doFilter(request, new MockHttpServletResponse(), capturingChain());
        Map<String, String> first = new HashMap<>(seen);

        // when
        for (DispatcherType type : new DispatcherType[] {DispatcherType.ASYNC, DispatcherType.ERROR}) {
            request.setDispatcherType(type);
            if (type == DispatcherType.ERROR) {
                request.setAttribute(org.springframework.web.util.WebUtils.ERROR_REQUEST_URI_ATTRIBUTE, "/api/v1/products");
            }
            request.removeAttribute(CorrelationFilter.class.getName() + ".FILTERED");
            seen.clear();
            filter.doFilter(request, new MockHttpServletResponse(), capturingChain());

            // then
            assertThat(seen).as(type.name()).isEqualTo(first);
        }
        assertThat(request.getAttribute(CorrelationFilter.ATTRIBUTE)).isInstanceOf(CorrelationIds.class);
    }

    @Test
    void doesNotTouchTheHeadersOfACommittedResponse() throws Exception {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        // when
        filter.doFilter(request("req", null), response, capturingChain());

        // then
        assertThat(response.getHeader("X-Request-Id")).isNull();
        assertThat(seen).containsEntry("requestId", "req");
    }

    private MockHttpServletRequest request(String requestId, String traceparent) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        if (requestId != null) {
            request.addHeader("X-Request-Id", requestId);
        }
        if (traceparent != null) {
            request.addHeader("traceparent", traceparent);
        }
        return request;
    }

    private MockFilterChain capturingChain() {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException, ServletException {
                seen.put("requestId", MDC.get("requestId"));
                seen.put("traceId", MDC.get("traceId"));
                seen.put("spanId", MDC.get("spanId"));
            }
        };
    }
}
