package com.awesome.testing.service.tender;

import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.service.giftcard.GiftCardService;
import com.awesome.testing.service.loyalty.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderOrderEventsTest {

    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private GiftCardService giftCardService;
    @Mock
    private LoyaltyService loyaltyService;
    @Mock
    private TenderRefundService tenderRefundService;

    @InjectMocks
    private TenderOrderEvents events;

    @Test
    void createdOrdersRecordTheirRedemptions() {
        OrderEntity order = tendered();
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        events.onCommerceEvent(event(CommerceEventType.ORDER_CREATED, Map.of()));

        verify(giftCardService).recordRedemption(order);
        verify(loyaltyService).recordRedemption(order);
    }

    @Test
    void createdOrdersWithoutTendersRecordNothing() {
        when(orderRepository.findById(42L)).thenReturn(Optional.of(plain()));

        events.onCommerceEvent(event(CommerceEventType.ORDER_CREATED, Map.of()));

        verifyNoInteractions(giftCardService, loyaltyService);
    }

    @Test
    void cancellationRestoresTendersAndRevokesAllEarnedPoints() {
        OrderEntity order = tendered();
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        events.onCommerceEvent(event(CommerceEventType.ORDER_CANCELLED, Map.of()));

        verify(tenderRefundService).restoreForCancellation(order);
        verify(loyaltyService).revokeEarned(order, "cancel:order:42", new BigDecimal("100.00"));
    }

    @Test
    void cancellationWithoutTendersOnlyRevokes() {
        OrderEntity order = plain();
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        events.onCommerceEvent(event(CommerceEventType.ORDER_CANCELLED, Map.of()));

        verify(tenderRefundService, never()).restoreForCancellation(any());
        verify(loyaltyService).revokeEarned(order, "cancel:order:42", new BigDecimal("100.00"));
    }

    @Test
    void refundsBeyondTheCardGoToTheTenders() {
        OrderEntity order = tendered();
        order.setRefundedAmount(new BigDecimal("60.00"));
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        events.onCommerceEvent(event(CommerceEventType.REFUND_BOOKED,
                Map.of("amount", new BigDecimal("60.00"), "requestId", REQUEST)));

        verify(tenderRefundService).refundToTenders(order, REQUEST, new BigDecimal("10.00"));
        verify(loyaltyService).revokeEarned(order, "refund:" + REQUEST, new BigDecimal("60.00"));
    }

    @Test
    void refundsTheCardCoversOnlyRevokePoints() {
        OrderEntity order = tendered();
        order.setRefundedAmount(new BigDecimal("50.00"));
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        events.onCommerceEvent(event(CommerceEventType.REFUND_BOOKED,
                Map.of("amount", new BigDecimal("50.00"), "requestId", REQUEST)));

        verify(tenderRefundService, never()).refundToTenders(any(), any(), any());
        verify(loyaltyService).revokeEarned(order, "refund:" + REQUEST, new BigDecimal("50.00"));
    }

    @ParameterizedTest
    @EnumSource(value = CommerceEventType.class, names = {"ORDER_PAID", "ORDER_SHIPPED", "ORDER_DELIVERED", "PAYMENT_FAILED"})
    void otherEventsAreIgnored(CommerceEventType type) {
        events.onCommerceEvent(event(type, Map.of()));

        verifyNoInteractions(orderRepository, giftCardService, loyaltyService, tenderRefundService);
    }

    private static OrderEntity tendered() {
        return OrderEntity.builder().id(42L).username("buyer").totalAmount(new BigDecimal("100.00"))
                .giftCardCode("GC-ABCD-EFGH-JKMN").giftCardAmount(new BigDecimal("30.00"))
                .pointsRedeemed(2_000L).pointsAmount(new BigDecimal("20.00")).build();
    }

    private static OrderEntity plain() {
        return OrderEntity.builder().id(42L).username("buyer").totalAmount(new BigDecimal("100.00")).build();
    }

    private static CommerceEvent event(CommerceEventType type, Map<String, Object> data) {
        return new CommerceEvent(UUID.randomUUID(), type, "buyer", 42L, Instant.now(), data);
    }
}
