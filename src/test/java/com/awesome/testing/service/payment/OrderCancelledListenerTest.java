package com.awesome.testing.service.payment;

import com.awesome.testing.entity.payment.OrderPaymentEntity;
import com.awesome.testing.entity.payment.OrderPaymentStatus;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.repository.payment.OrderPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancelledListenerTest {

    private static final UUID PAYMENT = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID REQUEST = OrderCancelledListener.requestId(6L);

    @Mock private OrderPaymentRepository paymentRepository;
    @Mock private PaymentRefundOutbox outbox;
    @InjectMocks private OrderCancelledListener listener;

    @Test
    void requestIdIsDerivedFromTheOrderId() {
        assertThat(REQUEST).isEqualTo(UUID.nameUUIDFromBytes("cancel:6".getBytes(StandardCharsets.UTF_8)));
        assertThat(OrderCancelledListener.requestId(7L)).isNotEqualTo(REQUEST);
    }

    @Test
    void refundsTheCapturedAmountMinusEarlierCardRefunds() {
        captured("40.00");
        when(outbox.cardRefunded(6L)).thenReturn(new BigDecimal("15.00"));

        listener.onOrderCancelled(event(CommerceEventType.ORDER_CANCELLED));

        verify(outbox).enqueue(6L, PAYMENT, REQUEST, new BigDecimal("25.00"), "Order cancelled");
    }

    @Test
    void nothingLeftOnTheCardQueuesNothing() {
        captured("40.00");
        when(outbox.cardRefunded(6L)).thenReturn(new BigDecimal("40.00"));

        listener.onOrderCancelled(event(CommerceEventType.ORDER_CANCELLED));

        verify(outbox, never()).enqueue(any(), any(), any(), any(), any());
    }

    @Test
    void anAlreadyQueuedCancelRefundIsNotQueuedAgain() {
        captured("40.00");
        when(outbox.isQueued(REQUEST)).thenReturn(true);

        listener.onOrderCancelled(event(CommerceEventType.ORDER_CANCELLED));

        verify(outbox, never()).cardRefunded(any());
        verify(outbox, never()).enqueue(any(), any(), any(), any(), any());
    }

    @Test
    void ordersWithoutACapturedPaymentAreSkipped() {
        when(paymentRepository.findFirstByOrderIdAndStatus(6L, OrderPaymentStatus.CAPTURED)).thenReturn(Optional.empty());

        listener.onOrderCancelled(event(CommerceEventType.ORDER_CANCELLED));

        verifyNoInteractions(outbox);
    }

    @Test
    void otherEventsAreIgnored() {
        listener.onOrderCancelled(event(CommerceEventType.REFUND_BOOKED));

        verifyNoInteractions(paymentRepository, outbox);
    }

    private void captured(String amount) {
        when(paymentRepository.findFirstByOrderIdAndStatus(6L, OrderPaymentStatus.CAPTURED)).thenReturn(Optional.of(
                OrderPaymentEntity.builder().paymentId(PAYMENT).amount(new BigDecimal(amount)).build()));
    }

    private static CommerceEvent event(CommerceEventType type) {
        return new CommerceEvent(UUID.randomUUID(), type, "buyer", 6L, Instant.now(), Map.of("orderId", 6L));
    }
}
