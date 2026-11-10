package com.awesome.testing.event;

import com.awesome.testing.entity.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes {@link CommerceEvent}s for an order. Call it inside the
 * transaction that changed the order, after the change is applied, so the
 * event carries the new status.
 */
@Component
@RequiredArgsConstructor
public class CommerceEvents {

    private final ApplicationEventPublisher eventPublisher;
    private final Clock commerceClock;

    public CommerceEvent publish(CommerceEventType type, OrderEntity order) {
        return publish(type, order, Map.of());
    }

    /**
     * @param extra event-specific {@code data} entries such as {@code amount},
     *              {@code paymentId} or {@code failureCode}; null values are kept
     */
    public CommerceEvent publish(CommerceEventType type, OrderEntity order, Map<String, ?> extra) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("status", order.getStatus().name());
        data.put("totalAmount", order.getTotalAmount());
        data.putAll(extra);
        CommerceEvent event = new CommerceEvent(
                UUID.randomUUID(),
                type,
                order.getUsername(),
                order.getId(),
                commerceClock.instant(),
                Collections.unmodifiableMap(data));
        eventPublisher.publishEvent(event);
        return event;
    }
}
