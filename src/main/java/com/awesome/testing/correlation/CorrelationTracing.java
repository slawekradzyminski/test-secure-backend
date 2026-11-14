package com.awesome.testing.correlation;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.util.Map;
import java.util.Optional;

/**
 * Bridges the hand-written correlation ids and the Micrometer tracer. With tracing off
 * ({@code TRACING_ENABLED=false}, the default) every method behaves exactly like the plain
 * {@link CorrelationIds} code before tracing existed. With tracing on, the MDC trace and span ids
 * are those of the request's server span or of the span resumed work runs in (parented by the
 * stored {@code traceparent}). The correlation code owns the MDC either way: Micrometer's MDC
 * listener is silenced in {@link TracingConfiguration}, as in warehouse-service.
 */
public class CorrelationTracing {

    /** Name of the span around one delayed or retried email send. */
    public static final String EMAIL_SPAN = "send email";
    /** Name of the span around one webhook delivery attempt. */
    public static final String WEBHOOK_SPAN = "deliver webhook";

    private static final CorrelationTracing OFF = new CorrelationTracing(false, null, null);

    private final boolean tracingEnabled;
    private final Tracer tracer;
    private final Propagator propagator;

    public CorrelationTracing(boolean enabled, Tracer tracer, Propagator propagator) {
        this.tracingEnabled = enabled;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** Tracing off: the behaviour of the correlation code without a tracer (unit tests). */
    public static CorrelationTracing disabled() {
        return OFF;
    }

    public boolean enabled() {
        return tracingEnabled;
    }

    /**
     * Ids of a request inside its server span: the request id of {@code provisional} (from
     * {@link CorrelationFilter}) with the server span's trace and span id. Empty with tracing off or
     * outside a span, when the provisional ids stay.
     */
    public Optional<CorrelationIds> forServerSpan(CorrelationIds provisional) {
        if (!tracingEnabled) {
            return Optional.empty();
        }
        return Optional.ofNullable(tracer.currentSpan()).map(Span::context)
                .map(context -> new CorrelationIds(provisional.requestId(), context.traceId(), context.spanId()));
    }

    /**
     * The {@code traceparent} to store with a hand-off (email event, webhook delivery): the current
     * span with its real sampling flag while tracing is on, otherwise the MDC ids as before.
     */
    public String currentTraceparent() {
        if (tracingEnabled) {
            Span current = tracer.currentSpan();
            if (current != null) {
                return traceparentOf(current.context());
            }
        }
        return CorrelationIds.currentTraceparent();
    }

    /**
     * Opens resumed work (an outbox dispatch) under the stored request id and {@code traceparent}.
     * Tracing off: {@link CorrelationIds#resume} in the MDC, nothing else. Tracing on: also starts a
     * {@code PRODUCER} span named {@code spanName}, the child of a valid stored {@code traceparent}
     * (a new trace otherwise), puts it in scope and uses its trace and span ids in the MDC. Closing
     * the scope ends the span.
     */
    public CorrelationIds.Scope resume(String spanName, String storedRequestId, String storedTraceparent) {
        if (!tracingEnabled) {
            return CorrelationIds.resume(storedRequestId, storedTraceparent).open();
        }
        Span.Builder builder = CorrelationIds.traceIdOf(storedTraceparent).isPresent()
                ? propagator.extract(Map.of(CorrelationIds.TRACEPARENT_HEADER, storedTraceparent), Map::get)
                : tracer.spanBuilder().setNoParent();
        Span span = builder.name(spanName).kind(Span.Kind.PRODUCER).start();
        TraceContext context = span.context();
        String requestId = CorrelationIds.isValidRequestId(storedRequestId)
                ? storedRequestId
                : CorrelationIds.newRequestId();
        // The span goes into scope first, then the MDC ids; closing undoes both and ends the span.
        return new TracedScope(span, tracer.withSpan(span),
                new CorrelationIds(requestId, context.traceId(), context.spanId()).open());
    }

    private record TracedScope(Span span, Tracer.SpanInScope inScope, CorrelationIds.Scope mdc)
            implements CorrelationIds.Scope {
        @Override
        public void close() {
            try (Tracer.SpanInScope ignored = inScope) {
                mdc.close();
            } finally {
                span.end();
            }
        }
    }

    /**
     * The {@code traceparent} to send on an outgoing call that no instrumented client makes (the
     * webhook sender). Tracing off: a child of {@code ids} with a new random parent id, as before.
     * Tracing on: the current span itself, with its real sampling flag.
     */
    public String outgoingTraceparent(CorrelationIds ids) {
        if (tracingEnabled) {
            Span current = tracer.currentSpan();
            if (current != null) {
                return traceparentOf(current.context());
            }
        }
        return ids.childTraceparent();
    }

    private static String traceparentOf(TraceContext context) {
        return "00-" + context.traceId() + "-" + context.spanId() + (isSampled(context) ? "-01" : "-00");
    }

    private static boolean isSampled(TraceContext context) {
        return Boolean.TRUE.equals(context.sampled());
    }
}
