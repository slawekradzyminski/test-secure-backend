package com.awesome.testing.correlation;

import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Outbox dispatch spans and the sampler, on a real OpenTelemetry SDK with an in-memory exporter. */
class CorrelationTracingTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PARENT_ID = "00f067aa0ba902b7";
    private static final String STORED = "00-" + TRACE_ID + "-" + PARENT_ID + "-01";

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider provider = SdkTracerProvider.builder()
            .setSampler(TracingSampler.create(true, 1.0))
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    private final CorrelationTracing tracing = tracing(provider);

    @AfterEach
    void tearDown() {
        MDC.clear();
        provider.close();
    }

    @Test
    void resumedWorkIsAProducerSpanChildOfTheStoredTraceparentAndOwnsTheMdc() {
        // given
        Map<String, String> mdc = new HashMap<>();
        String outgoing;

        // when
        try (CorrelationIds.Scope ignored = tracing.resume(CorrelationTracing.WEBHOOK_SPAN, "queued-req", STORED)) {
            mdc.putAll(MDC.getCopyOfContextMap());
            outgoing = tracing.outgoingTraceparent(CorrelationIds.current().orElseThrow());
        }

        // then
        SpanData span = exporter.getFinishedSpanItems().getFirst();
        assertThat(span.getName()).isEqualTo("deliver webhook");
        assertThat(span.getKind()).isEqualTo(SpanKind.PRODUCER);
        assertThat(span.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(span.getParentSpanId()).isEqualTo(PARENT_ID);
        assertThat(mdc).containsEntry("requestId", "queued-req").containsEntry("traceId", TRACE_ID)
                .containsEntry("spanId", span.getSpanId());
        assertThat(outgoing).isEqualTo("00-" + TRACE_ID + "-" + span.getSpanId() + "-01");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void workWithoutAStoredTraceStartsANewSampledTrace() {
        // given
        Map<String, String> mdc = new HashMap<>();

        // when
        try (CorrelationIds.Scope ignored = tracing.resume(CorrelationTracing.EMAIL_SPAN, null, "garbage")) {
            mdc.putAll(MDC.getCopyOfContextMap());
        }

        // then
        SpanData span = exporter.getFinishedSpanItems().getFirst();
        assertThat(span.getName()).isEqualTo("send email");
        assertThat(span.getParentSpanContext().isValid()).isFalse();
        assertThat(mdc.get("traceId")).isEqualTo(span.getTraceId()).isNotEqualTo(TRACE_ID);
        assertThat(mdc.get("requestId")).hasSize(36);
    }

    @Test
    void anUnsampledStoredTraceIsNotExported() {
        // when
        String outgoing;
        try (CorrelationIds.Scope ignored = tracing.resume(CorrelationTracing.EMAIL_SPAN, "r",
                "00-" + TRACE_ID + "-" + PARENT_ID + "-00")) {
            outgoing = tracing.outgoingTraceparent(CorrelationIds.current().orElseThrow());
        }

        // then
        assertThat(exporter.getFinishedSpanItems()).isEmpty();
        assertThat(outgoing).startsWith("00-" + TRACE_ID + "-").endsWith("-00");
    }

    @Test
    void disabledTracingIsTheHandWrittenCorrelationOnly() {
        // given
        CorrelationTracing disabled = CorrelationTracing.disabled();
        Map<String, String> mdc = new HashMap<>();
        String outgoing;

        // when
        try (CorrelationIds.Scope ignored = disabled.resume(CorrelationTracing.EMAIL_SPAN, "queued-req", STORED)) {
            mdc.putAll(MDC.getCopyOfContextMap());
            outgoing = disabled.outgoingTraceparent(CorrelationIds.current().orElseThrow());
        }

        // then
        assertThat(disabled.enabled()).isFalse();
        assertThat(mdc).containsEntry("requestId", "queued-req").containsEntry("traceId", TRACE_ID);
        assertThat(mdc.get("spanId")).matches("[0-9a-f]{16}").isNotEqualTo(PARENT_ID);
        assertThat(outgoing).matches("00-" + TRACE_ID + "-[0-9a-f]{16}-01").doesNotContain(mdc.get("spanId"));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void enabledButWithoutACurrentSpanTheOutgoingTraceparentIsTheHandWrittenChild() {
        // given
        CorrelationIds ids = new CorrelationIds("r", TRACE_ID, PARENT_ID);

        // when
        String outgoing = tracing.outgoingTraceparent(ids);

        // then
        assertThat(outgoing).matches("00-" + TRACE_ID + "-[0-9a-f]{16}-01").doesNotContain(PARENT_ID);
    }

    @Test
    void theSamplerRecordsNothingWhileTracingIsOff() {
        Sampler off = TracingSampler.create(false, 1.0);

        assertThat(decision(off, sampledParent(), SpanKind.SERVER)).isEqualTo(SamplingDecision.DROP);
        assertThat(decision(off, Context.root(), SpanKind.SERVER)).isEqualTo(SamplingDecision.DROP);
    }

    @Test
    void theSamplerFollowsTheParentAndDropsInternalRoots() {
        Sampler on = TracingSampler.create(true, 1.0);
        Sampler never = TracingSampler.create(true, 0.0);

        assertThat(decision(on, Context.root(), SpanKind.SERVER)).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
        assertThat(decision(on, Context.root(), SpanKind.PRODUCER)).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
        assertThat(decision(on, Context.root(), SpanKind.INTERNAL)).as("@Scheduled tick").isEqualTo(SamplingDecision.DROP);
        assertThat(decision(on, sampledParent(), SpanKind.INTERNAL)).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
        assertThat(decision(never, Context.root(), SpanKind.SERVER)).isEqualTo(SamplingDecision.DROP);
        assertThat(decision(never, sampledParent(), SpanKind.SERVER)).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
        assertThat(on.getDescription()).contains("internal roots dropped");
    }

    private static SamplingDecision decision(Sampler sampler, Context parent, SpanKind kind) {
        return sampler.shouldSample(parent, TRACE_ID, "span", kind, Attributes.empty(), List.of()).getDecision();
    }

    private static Context sampledParent() {
        return Context.root().with(io.opentelemetry.api.trace.Span.wrap(
                SpanContext.createFromRemoteParent(TRACE_ID, PARENT_ID, TraceFlags.getSampled(), TraceState.getDefault())));
    }

    private static CorrelationTracing tracing(SdkTracerProvider provider) {
        io.opentelemetry.api.trace.Tracer otel = provider.get("test");
        OtelCurrentTraceContext context = new OtelCurrentTraceContext();
        OtelTracer tracer = new OtelTracer(otel, context, event -> { },
                new OtelBaggageManager(context, List.of(), List.of()));
        OtelPropagator propagator = new OtelPropagator(
                ContextPropagators.create(W3CTraceContextPropagator.getInstance()), otel);
        return new CorrelationTracing(true, tracer, propagator);
    }
}
