package com.awesome.testing.correlation;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerSpanCorrelationFilterTest {

    private static final CorrelationIds PROVISIONAL = new CorrelationIds("req-1", "1".repeat(32), "2".repeat(16));
    private static final CorrelationIds SERVER = new CorrelationIds("req-1", "3".repeat(32), "4".repeat(16));

    private final Map<String, String> seen = new HashMap<>();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void withTracingOnTheServerSpanIdsReplaceTheProvisionalOnesForTheRestOfTheRequest() throws Exception {
        // given
        CorrelationTracing tracing = mock(CorrelationTracing.class);
        when(tracing.forServerSpan(PROVISIONAL)).thenReturn(Optional.of(SERVER));
        MockHttpServletRequest request = request();

        // when
        try (CorrelationIds.Scope ignored = PROVISIONAL.open()) {
            new ServerSpanCorrelationFilter(tracing).doFilter(request, new MockHttpServletResponse(), capturingChain());
            // then: the provisional ids are back once the request leaves the filter
            assertThat(MDC.get("traceId")).isEqualTo(PROVISIONAL.traceId());
        }
        assertThat(seen).containsEntry("requestId", "req-1").containsEntry("traceId", SERVER.traceId())
                .containsEntry("spanId", SERVER.spanId());
        assertThat(request.getAttribute(CorrelationFilter.ATTRIBUTE)).isEqualTo(SERVER);
    }

    @Test
    void withTracingOffTheRequestPassesUnchanged() throws Exception {
        // given
        MockHttpServletRequest request = request();

        // when
        try (CorrelationIds.Scope ignored = PROVISIONAL.open()) {
            new ServerSpanCorrelationFilter(CorrelationTracing.disabled())
                    .doFilter(request, new MockHttpServletResponse(), capturingChain());
        }

        // then
        assertThat(seen).containsEntry("traceId", PROVISIONAL.traceId()).containsEntry("spanId", PROVISIONAL.spanId());
        assertThat(request.getAttribute(CorrelationFilter.ATTRIBUTE)).isEqualTo(PROVISIONAL);
    }

    @Test
    void aRequestWithoutCorrelationIdsPassesUnchanged() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");

        // when
        new ServerSpanCorrelationFilter(mock(CorrelationTracing.class))
                .doFilter(request, new MockHttpServletResponse(), capturingChain());

        // then
        assertThat(seen).isEmpty();
    }

    @Test
    void handlesAsyncAndErrorDispatches() throws Exception {
        // given
        CorrelationTracing tracing = mock(CorrelationTracing.class);
        when(tracing.forServerSpan(PROVISIONAL)).thenReturn(Optional.of(SERVER));
        MockHttpServletRequest request = request();
        request.setDispatcherType(DispatcherType.ERROR);

        // when
        new ServerSpanCorrelationFilter(tracing).doFilter(request, new MockHttpServletResponse(), capturingChain());

        // then
        assertThat(seen).containsEntry("spanId", SERVER.spanId());
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setAttribute(CorrelationFilter.ATTRIBUTE, PROVISIONAL);
        return request;
    }

    private MockFilterChain capturingChain() {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                Map<String, String> mdc = MDC.getCopyOfContextMap();
                if (mdc != null) {
                    seen.putAll(mdc);
                }
            }
        };
    }
}
