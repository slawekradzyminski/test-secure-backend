package com.awesome.testing.event;

import com.awesome.testing.correlation.CorrelationIds;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.repository.event.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Appends every {@link CommerceEvent} to the {@code domain_events} feed. A plain, synchronous
 * {@link EventListener} on purpose: the row joins the transaction that published the event, so
 * it commits or rolls back with the state change. Runs first so an event is recorded before the
 * events other listeners publish in reaction to it. The request id, trace id and span id of the
 * writing request are taken from the MDC (all null for scheduled work without a request).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventRecorder {

    private final DomainEventRepository repository;
    private final Clock commerceClock;

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void record(CommerceEvent event) {
        DomainEventEntity saved = repository.save(DomainEventEntity.builder()
                .eventId(event.id())
                .type(event.type().wireName())
                .orderId(event.orderId())
                .username(event.username())
                .occurredAt(event.occurredAt().truncatedTo(ChronoUnit.MILLIS))
                .recordedAt(commerceClock.instant())
                .payload(DomainEventPayloads.data(event.data()))
                .requestId(MDC.get(CorrelationIds.MDC_REQUEST_ID))
                .traceId(MDC.get(CorrelationIds.MDC_TRACE_ID))
                .spanId(MDC.get(CorrelationIds.MDC_SPAN_ID))
                .build());
        // Logged in the writing request's MDC, so one search by request or trace id finds the backend side too.
        log.info("Recorded {} #{} for order {}", saved.getType(), saved.getSequence(), saved.getOrderId());
    }
}
