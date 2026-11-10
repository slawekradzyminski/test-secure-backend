package com.awesome.testing.service.loyalty;

import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyEarningListenerTest {

    @Mock
    private LoyaltyService loyaltyService;

    @InjectMocks
    private LoyaltyEarningListener listener;

    @Test
    void earnsOnOrderPaid() {
        listener.onOrderPaid(event(CommerceEventType.ORDER_PAID, 5L));

        verify(loyaltyService).earnForOrder(5L);
    }

    @Test
    void ignoresOtherEventsAndEventsWithoutAnOrder() {
        listener.onOrderPaid(event(CommerceEventType.ORDER_CREATED, 5L));
        listener.onOrderPaid(event(CommerceEventType.ORDER_PAID, null));

        verifyNoInteractions(loyaltyService);
    }

    @Test
    void failuresDoNotReachThePayingRequest() {
        when(loyaltyService.earnForOrder(5L)).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> listener.onOrderPaid(event(CommerceEventType.ORDER_PAID, 5L))).doesNotThrowAnyException();
    }

    private static CommerceEvent event(CommerceEventType type, Long orderId) {
        return new CommerceEvent(UUID.randomUUID(), type, "buyer", orderId, Instant.now(), Map.of());
    }
}
