package com.awesome.testing.correlation;

import org.slf4j.MDC;

import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * The correlation of one unit of work: the {@code X-Request-Id} of the request that started it and
 * the W3C trace context ({@code traceparent}) it belongs to. {@code spanId} identifies this hop;
 * every hop (request, scheduled send, outbound call) gets a new one under the same trace id.
 *
 * <p>While work runs, the ids live in the SLF4J MDC under {@value #MDC_REQUEST_ID},
 * {@value #MDC_TRACE_ID} and {@value #MDC_SPAN_ID}, so every log line carries them (pattern
 * {@code [%X{traceId},%X{requestId}]}, or JSON fields with structured logging).
 */
public record CorrelationIds(String requestId, String traceId, String spanId) {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final int MAX_REQUEST_ID_LENGTH = 128;

    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern TRACEPARENT = Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}");
    private static final String ZERO_TRACE_ID = "0".repeat(32);
    private static final String ZERO_SPAN_ID = "0".repeat(16);
    private static final HexFormat HEX = HexFormat.of();

    /** Restores the MDC entries that were present before {@link #open()}. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * The ids of an incoming request: a valid {@code X-Request-Id} and the trace id of a valid
     * {@code traceparent} are kept, anything missing or invalid is generated; the span is new.
     */
    public static CorrelationIds fromHeaders(String requestIdHeader, String traceparentHeader) {
        return new CorrelationIds(
                isValidRequestId(requestIdHeader) ? requestIdHeader : newRequestId(),
                traceIdOf(traceparentHeader).orElseGet(CorrelationIds::newTraceId),
                newSpanId());
    }

    /**
     * Ids to resume work stored earlier (an outbox row) under: the stored request id and trace id
     * when valid, otherwise new ones, and always a new span.
     */
    public static CorrelationIds resume(String storedRequestId, String storedTraceparent) {
        return fromHeaders(storedRequestId, storedTraceparent);
    }

    /** The ids of the work running on this thread, if any. */
    public static Optional<CorrelationIds> current() {
        String requestId = MDC.get(MDC_REQUEST_ID);
        String traceId = MDC.get(MDC_TRACE_ID);
        if (requestId == null || traceId == null) {
            return Optional.empty();
        }
        String spanId = MDC.get(MDC_SPAN_ID);
        return Optional.of(new CorrelationIds(requestId, traceId, spanId == null ? newSpanId() : spanId));
    }

    /** Request id of the work running on this thread, or null. */
    public static String currentRequestId() {
        return current().map(CorrelationIds::requestId).orElse(null);
    }

    /** Trace id of the work running on this thread, or null. */
    public static String currentTraceId() {
        return current().map(CorrelationIds::traceId).orElse(null);
    }

    /** {@code traceparent} of the work running on this thread (its span as parent), or null. */
    public static String currentTraceparent() {
        return current().map(CorrelationIds::traceparent).orElse(null);
    }

    public static boolean isValidRequestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    /** The trace id of a valid version-00 {@code traceparent} with non-zero ids. */
    public static Optional<String> traceIdOf(String traceparent) {
        if (traceparent == null) {
            return Optional.empty();
        }
        java.util.regex.Matcher matcher = TRACEPARENT.matcher(traceparent);
        if (!matcher.matches() || ZERO_TRACE_ID.equals(matcher.group(1)) || ZERO_SPAN_ID.equals(matcher.group(2))) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    public static String newTraceId() {
        return nonZeroHex(2);
    }

    public static String newSpanId() {
        return nonZeroHex(1);
    }

    private static String nonZeroHex(int longs) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (true) {
            StringBuilder hex = new StringBuilder(longs * 16);
            boolean zero = true;
            for (int i = 0; i < longs; i++) {
                long value = random.nextLong();
                zero &= value == 0;
                hex.append(HEX.toHexDigits(value));
            }
            if (!zero) {
                return hex.toString();
            }
        }
    }

    /** {@code traceparent} naming this hop's span as the parent. */
    public String traceparent() {
        return "00-" + traceId + "-" + spanId + "-01";
    }

    /** {@code traceparent} for an outbound call: same trace, a new parent id. */
    public String childTraceparent() {
        return "00-" + traceId + "-" + newSpanId() + "-01";
    }

    /** Puts the ids into the MDC until the returned scope is closed. */
    public Scope open() {
        String previousRequestId = MDC.get(MDC_REQUEST_ID);
        String previousTraceId = MDC.get(MDC_TRACE_ID);
        String previousSpanId = MDC.get(MDC_SPAN_ID);
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_SPAN_ID, spanId);
        return () -> {
            restore(MDC_REQUEST_ID, previousRequestId);
            restore(MDC_TRACE_ID, previousTraceId);
            restore(MDC_SPAN_ID, previousSpanId);
        };
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previous);
        }
    }
}
