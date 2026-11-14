package com.awesome.testing.correlation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Optional;

/**
 * Sends the caller's {@code X-Request-Id} and a child {@code traceparent} (same trace id, new
 * parent id) on every {@code WebClient} call. Reactor may run the exchange on a Netty thread
 * without the caller's MDC, so the ids are taken from the Reactor context first (see
 * {@link #withCurrentIds(Flux)}) and from the MDC of the subscribing thread otherwise.
 *
 * <p>With tracing on the {@code WebClient} is instrumented and its client span already carries the
 * {@code traceparent}, so this filter is created with {@code handwrittenTraceparent=false} and only
 * adds {@code X-Request-Id}.
 */
public class CorrelationExchangeFilter implements ExchangeFilterFunction {

    private final boolean handwrittenTraceparent;

    /** Tracing off: sends {@code X-Request-Id} and the hand-written child {@code traceparent}. */
    public CorrelationExchangeFilter() {
        this(true);
    }

    public CorrelationExchangeFilter(boolean handwrittenTraceparent) {
        this.handwrittenTraceparent = handwrittenTraceparent;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.deferContextual(context -> {
            Optional<CorrelationIds> ids = context.getOrEmpty(CorrelationIds.class)
                    .map(CorrelationIds.class::cast)
                    .or(CorrelationIds::current);
            if (ids.isEmpty()) {
                return next.exchange(request);
            }
            return next.exchange(ClientRequest.from(request)
                    .headers(headers -> {
                        headers.set(CorrelationIds.REQUEST_ID_HEADER, ids.get().requestId());
                        if (handwrittenTraceparent) {
                            headers.set(CorrelationIds.TRACEPARENT_HEADER, ids.get().childTraceparent());
                        }
                    })
                    .build());
        });
    }

    /**
     * Captures the ids of the calling thread now (at assembly) into the Reactor context of the
     * returned publisher, so every exchange it makes, on any thread, carries them. The current
     * observation (the server request's, when one is open) is captured too, so an instrumented
     * {@code WebClient} parents its client span on it even when the exchange runs on a Netty thread;
     * an uninstrumented client ignores it.
     */
    public static <T> Flux<T> withCurrentIds(Flux<T> publisher) {
        Optional<CorrelationIds> ids = CorrelationIds.current();
        Observation observation = ObservationThreadLocalAccessor.getInstance().getValue();
        if (ids.isEmpty() && observation == null) {
            return publisher;
        }
        return publisher.contextWrite(context -> put(put(context, CorrelationIds.class, ids.orElse(null)),
                ObservationThreadLocalAccessor.KEY, observation));
    }

    private static Context put(Context context, Object key, Object value) {
        return value == null || context.hasKey(key) ? context : context.put(key, value);
    }
}
