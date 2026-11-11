package com.awesome.testing.event;

import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommerceEventsTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final CommerceEvents events = new CommerceEvents(publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    private final OrderEntity order = OrderEntity.builder().id(7L).username("buyer").status(OrderStatus.PAID)
            .totalAmount(new BigDecimal("49.99")).build();

    @Test
    void publishesTheOrderSnapshotWithTheStandardDataKeys() {
        CommerceEvent event = events.publish(CommerceEventType.ORDER_PAID, order);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(published.capture());
        assertThat(published.getValue()).isSameAs(event);
        assertThat(event.type()).isEqualTo(CommerceEventType.ORDER_PAID);
        assertThat(event.username()).isEqualTo("buyer");
        assertThat(event.orderId()).isEqualTo(7L);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.id()).isNotNull().isNotEqualTo(events.publish(CommerceEventType.ORDER_PAID, order).id());
        assertThat(event.data()).containsExactly(
                Map.entry("orderId", 7L),
                Map.entry("status", "PAID"),
                Map.entry("totalAmount", new BigDecimal("49.99")));
    }

    @Test
    void extraDataIsAppendedIncludingNullsAndTheResultIsReadOnly() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("paymentId", "p-1");
        extra.put("failureCode", null);

        CommerceEvent event = events.publish(CommerceEventType.PAYMENT_FAILED, order, extra);

        assertThat(event.data()).containsKeys("orderId", "status", "totalAmount", "paymentId", "failureCode")
                .containsEntry("paymentId", "p-1")
                .containsEntry("failureCode", null);
        assertThatThrownBy(() -> event.data().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void publishDataKeepsExactlyTheGivenDataInOrderAndCopiesIt() {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("requestId", "r-1");
        data.put("orderId", 7L);
        data.put("amount", new BigDecimal("5.00"));
        data.put("reason", null);

        CommerceEvent event = events.publishData(CommerceEventType.CARD_REFUND_REQUESTED, "buyer", 7L, data);
        data.put("later", 1);

        verify(publisher).publishEvent(event);
        assertThat(event.type()).isEqualTo(CommerceEventType.CARD_REFUND_REQUESTED);
        assertThat(event.username()).isEqualTo("buyer");
        assertThat(event.orderId()).isEqualTo(7L);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.id()).isNotNull();
        assertThat(event.data().keySet()).containsExactly("requestId", "orderId", "amount", "reason");
        assertThat(event.data()).containsEntry("reason", null);
        assertThatThrownBy(() -> event.data().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }
}
