package com.awesome.testing.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Something that happened to an order, published with
 * {@link org.springframework.context.ApplicationEventPublisher} inside the
 * transaction that changed it. Consumers listen with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so rolled-back
 * changes publish nothing. Create instances through {@link CommerceEvents}.
 *
 * @param data always holds {@code orderId}, {@code status} and {@code totalAmount};
 *             refunds add {@code amount}, payments add {@code paymentId} and {@code failureCode}
 */
public record CommerceEvent(
        UUID id,
        CommerceEventType type,
        String username,
        Long orderId,
        Instant occurredAt,
        Map<String, Object> data) {
}
