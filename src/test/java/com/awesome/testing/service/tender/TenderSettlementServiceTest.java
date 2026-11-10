package com.awesome.testing.service.tender;

import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderSettlementServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private TenderSettlementService service;

    @Test
    void fullyTenderedPendingOrdersBecomePaid() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order(OrderStatus.PENDING, "30.00", "20.00")));

        assertThat(service.settleIfFullyTendered(1L)).isTrue();

        verify(orderService).updateOrderStatus(1L, OrderStatus.PAID);
    }

    @Test
    void ordersWithMoneyStillDueStayPending() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order(OrderStatus.PENDING, "30.00", "19.99")));

        assertThat(service.settleIfFullyTendered(1L)).isFalse();

        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void ordersWithoutTendersOrNotPendingOrMissingAreLeftAlone() {
        OrderEntity free = OrderEntity.builder().id(2L).status(OrderStatus.PENDING).totalAmount(BigDecimal.ZERO).build();
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order(OrderStatus.CANCELLED, "50.00", null)));
        when(orderRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(free));
        when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.empty());

        assertThat(service.settleIfFullyTendered(1L)).isFalse();
        assertThat(service.settleIfFullyTendered(2L)).isFalse();
        assertThat(service.settleIfFullyTendered(3L)).isFalse();

        verifyNoInteractions(orderService);
    }

    @Test
    void listenerSettlesOnlyCreatedOrdersAndSwallowsFailures() {
        TenderSettlementService settlement = mock(TenderSettlementService.class);
        FullyTenderedOrderListener listener = new FullyTenderedOrderListener(settlement);
        when(settlement.settleIfFullyTendered(9L)).thenThrow(new IllegalStateException("boom"));

        listener.onOrderCreated(event(CommerceEventType.ORDER_PAID, 8L));
        listener.onOrderCreated(event(CommerceEventType.ORDER_CREATED, null));
        assertThatCode(() -> listener.onOrderCreated(event(CommerceEventType.ORDER_CREATED, 9L))).doesNotThrowAnyException();

        verify(settlement).settleIfFullyTendered(9L);
        verify(settlement, never()).settleIfFullyTendered(8L);
    }

    @Test
    void eraserForgetsCartLoyaltyAndRecipient() {
        CartTenderService cart = mock(CartTenderService.class);
        com.awesome.testing.service.loyalty.LoyaltyService loyalty = mock(com.awesome.testing.service.loyalty.LoyaltyService.class);
        com.awesome.testing.service.giftcard.GiftCardService giftCards = mock(com.awesome.testing.service.giftcard.GiftCardService.class);

        new TenderDataEraser(cart, loyalty, giftCards).forget("gone");

        verify(cart).clear("gone");
        verify(loyalty).forget("gone");
        verify(giftCards).forgetRecipient("gone");
    }

    private static OrderEntity order(OrderStatus status, String giftCard, String points) {
        return OrderEntity.builder().id(1L).status(status).totalAmount(new BigDecimal("50.00"))
                .giftCardCode("GC-ABCD-EFGH-JKMN").giftCardAmount(new BigDecimal(giftCard))
                .pointsRedeemed(points == null ? null : new BigDecimal(points).movePointRight(2).longValueExact())
                .pointsAmount(points == null ? null : new BigDecimal(points)).build();
    }

    private static CommerceEvent event(CommerceEventType type, Long orderId) {
        return new CommerceEvent(UUID.randomUUID(), type, "buyer", orderId, Instant.now(), Map.of());
    }
}
