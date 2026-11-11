package com.awesome.testing.correlation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdsTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PARENT_ID = "00f067aa0ba902b7";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + PARENT_ID + "-01";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "smoke-123", "A.b_c-9", "3f2c1a9e-7d4b-4c55-9a0e-2b6f8d1c4e77"})
    void acceptsRequestIdsOfTheAllowedCharacters(String value) {
        assertThat(CorrelationIds.isValidRequestId(value)).isTrue();
    }

    @Test
    void acceptsRequestIdsOfExactly128Characters() {
        assertThat(CorrelationIds.isValidRequestId("x".repeat(128))).isTrue();
        assertThat(CorrelationIds.isValidRequestId("x".repeat(129))).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "has space", "semi;colon", "slash/", "ünicode", "line\nbreak", "a\tb", "<script>"})
    void rejectsOtherRequestIds(String value) {
        assertThat(CorrelationIds.isValidRequestId(value)).isFalse();
    }

    @Test
    void readsTheTraceIdOfAValidTraceparent() {
        assertThat(CorrelationIds.traceIdOf(TRACEPARENT)).contains(TRACE_ID);
        assertThat(CorrelationIds.traceIdOf("00-" + TRACE_ID + "-" + PARENT_ID + "-00")).contains(TRACE_ID);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01",
            "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
            "00-4bf92f3577b34da6a3ce929d0e0e473-00f067aa0ba902b7-01",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b-01",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-1",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra",
            "00-4bf92f3577b34da6a3ce929d0e0e473g-00f067aa0ba902b7-01",
            "garbage"})
    void rejectsInvalidTraceparents(String value) {
        assertThat(CorrelationIds.traceIdOf(value)).isEmpty();
    }

    @Test
    void generatesIdsInTheirWireFormats() {
        Set<String> spans = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            assertThat(CorrelationIds.newRequestId())
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}")
                    .satisfies(id -> assertThat(CorrelationIds.isValidRequestId(id)).isTrue());
            assertThat(CorrelationIds.newTraceId()).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
            String span = CorrelationIds.newSpanId();
            assertThat(span).matches("[0-9a-f]{16}").isNotEqualTo("0".repeat(16));
            spans.add(span);
        }
        assertThat(spans).hasSizeGreaterThan(45);
    }

    @Test
    void keepsValidIncomingHeadersAndStartsANewSpan() {
        CorrelationIds ids = CorrelationIds.fromHeaders("smoke-123", TRACEPARENT);

        assertThat(ids.requestId()).isEqualTo("smoke-123");
        assertThat(ids.traceId()).isEqualTo(TRACE_ID);
        assertThat(ids.spanId()).matches("[0-9a-f]{16}").isNotEqualTo(PARENT_ID);
    }

    @Test
    void replacesInvalidIncomingHeaders() {
        CorrelationIds ids = CorrelationIds.fromHeaders("bad id", "00-zz-zz-01");

        assertThat(CorrelationIds.isValidRequestId(ids.requestId())).isTrue();
        assertThat(ids.requestId()).isNotEqualTo("bad id").hasSize(36);
        assertThat(ids.traceId()).matches("[0-9a-f]{32}");
    }

    @Test
    void resumesStoredIdsWithANewSpanOrStartsFreshWithoutThem() {
        CorrelationIds resumed = CorrelationIds.resume("req-1", TRACEPARENT);
        CorrelationIds fresh = CorrelationIds.resume(null, null);

        assertThat(resumed.requestId()).isEqualTo("req-1");
        assertThat(resumed.traceId()).isEqualTo(TRACE_ID);
        assertThat(resumed.spanId()).isNotEqualTo(PARENT_ID);
        assertThat(fresh.requestId()).hasSize(36);
        assertThat(fresh.traceId()).matches("[0-9a-f]{32}");
    }

    @Test
    void formatsItsOwnAndChildTraceparents() {
        CorrelationIds ids = new CorrelationIds("req", TRACE_ID, PARENT_ID);

        assertThat(ids.traceparent()).isEqualTo(TRACEPARENT);
        String child = ids.childTraceparent();
        assertThat(child).matches("00-" + TRACE_ID + "-[0-9a-f]{16}-01").isNotEqualTo(TRACEPARENT);
        assertThat(CorrelationIds.traceIdOf(child)).contains(TRACE_ID);
    }

    @Test
    void opensIntoTheMdcAndRestoresWhatWasThereBefore() {
        MDC.put(CorrelationIds.MDC_REQUEST_ID, "outer");
        MDC.put(CorrelationIds.MDC_TRACE_ID, "outer-trace");
        CorrelationIds ids = new CorrelationIds("inner", TRACE_ID, PARENT_ID);

        try (CorrelationIds.Scope ignored = ids.open()) {
            assertThat(MDC.get(CorrelationIds.MDC_REQUEST_ID)).isEqualTo("inner");
            assertThat(MDC.get(CorrelationIds.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
            assertThat(MDC.get(CorrelationIds.MDC_SPAN_ID)).isEqualTo(PARENT_ID);
            assertThat(CorrelationIds.current()).contains(ids);
        }

        assertThat(MDC.get(CorrelationIds.MDC_REQUEST_ID)).isEqualTo("outer");
        assertThat(MDC.get(CorrelationIds.MDC_TRACE_ID)).isEqualTo("outer-trace");
        assertThat(MDC.get(CorrelationIds.MDC_SPAN_ID)).isNull();
    }

    @Test
    void currentIsEmptyWithoutBothIds() {
        assertThat(CorrelationIds.current()).isEmpty();
        assertThat(CorrelationIds.currentRequestId()).isNull();
        assertThat(CorrelationIds.currentTraceId()).isNull();
        assertThat(CorrelationIds.currentTraceparent()).isNull();

        MDC.put(CorrelationIds.MDC_REQUEST_ID, "only-request");
        assertThat(CorrelationIds.current()).isEmpty();

        MDC.remove(CorrelationIds.MDC_REQUEST_ID);
        MDC.put(CorrelationIds.MDC_TRACE_ID, TRACE_ID);
        assertThat(CorrelationIds.current()).isEmpty();
    }

    @Test
    void currentReadsTheMdcAndInventsASpanWhenMissing() {
        MDC.put(CorrelationIds.MDC_REQUEST_ID, "req");
        MDC.put(CorrelationIds.MDC_TRACE_ID, TRACE_ID);

        assertThat(CorrelationIds.currentRequestId()).isEqualTo("req");
        assertThat(CorrelationIds.currentTraceId()).isEqualTo(TRACE_ID);
        assertThat(CorrelationIds.current()).hasValueSatisfying(ids -> assertThat(ids.spanId()).matches("[0-9a-f]{16}"));

        MDC.put(CorrelationIds.MDC_SPAN_ID, PARENT_ID);
        assertThat(CorrelationIds.currentTraceparent()).isEqualTo(TRACEPARENT);
    }
}
