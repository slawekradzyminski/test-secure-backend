package com.awesome.testing.correlation;

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
 */
public class CorrelationExchangeFilter implements ExchangeFilterFunction {

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
                        headers.set(CorrelationIds.TRACEPARENT_HEADER, ids.get().childTraceparent());
                    })
                    .build());
        });
    }

    /**
     * Captures the ids of the calling thread now (at assembly) into the Reactor context of the
     * returned publisher, so every exchange it makes, on any thread, carries them.
     */
    public static <T> Flux<T> withCurrentIds(Flux<T> publisher) {
        Optional<CorrelationIds> ids = CorrelationIds.current();
        return ids.isEmpty() ? publisher : publisher.contextWrite(context -> put(context, ids.get()));
    }

    private static Context put(Context context, CorrelationIds ids) {
        return context.hasKey(CorrelationIds.class) ? context : context.put(CorrelationIds.class, ids);
    }
}
