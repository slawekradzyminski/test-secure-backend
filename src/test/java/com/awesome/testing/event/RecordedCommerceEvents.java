package com.awesome.testing.event;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test listener registered in the shared test context. It consumes commerce
 * events the way real consumers must, after the transaction commits, so rolled
 * back changes never show up here.
 */
public class RecordedCommerceEvents {

    private final List<CommerceEvent> events = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void record(CommerceEvent event) {
        events.add(event);
    }

    public List<CommerceEvent> forOrder(Long orderId) {
        return events.stream().filter(event -> orderId.equals(event.orderId())).toList();
    }

    public List<CommerceEventType> typesForOrder(Long orderId) {
        return forOrder(orderId).stream().map(CommerceEvent::type).toList();
    }
}
