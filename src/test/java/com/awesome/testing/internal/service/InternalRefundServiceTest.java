package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.refund.OrderRefundEntity;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.internal.dto.OrderRefundDto;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.refund.OrderRefundRepository;
import com.awesome.testing.service.coupon.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalRefundServiceTest {

    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Mock private OrderRepository orderRepository;
    @Mock private OrderRefundRepository refundRepository;
    @Mock private CouponService couponService;
    @Mock private CommerceEvents commerceEvents;
    @InjectMocks private InternalRefundService service;

    private OrderEntity order;

    @BeforeEach
    void setUp() {
        order = OrderEntity.builder().id(7L).username("buyer").status(OrderStatus.DELIVERED)
                .shippingAddress(new AddressEntity()).items(new ArrayList<>())
                .totalAmount(new BigDecimal("50.00")).refundedAmount(new BigDecimal("10.00")).build();
        lenient().when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aBookedRefundPublishesRefundBookedWithItsAmount() {
        var result = service.refund(7L, new OrderRefundDto(REQUEST, new BigDecimal("15.00"), " Return #1 "));

        assertThat(result.refundedAmount()).isEqualByComparingTo("25.00");
        verify(commerceEvents).publish(CommerceEventType.REFUND_BOOKED, order,
                Map.of("amount", new BigDecimal("15.00"), "requestId", REQUEST));
        verify(couponService, never()).releaseRedemption(any());
    }

    @Test
    void aFullRefundReleasesTheCoupon() {
        service.refund(7L, new OrderRefundDto(REQUEST, new BigDecimal("40.00"), "Return #1"));

        verify(couponService).releaseRedemption(order);
    }

    @Test
    void replaysAndRejectedRefundsPublishNothing() {
        when(refundRepository.findByRequestId(REQUEST)).thenReturn(Optional.of(
                OrderRefundEntity.builder().order(order).requestId(REQUEST).amount(new BigDecimal("5.00")).build()));
        UUID other = UUID.randomUUID();

        var replay = service.refund(7L, new OrderRefundDto(REQUEST, new BigDecimal("5.00"), "Return #1"));
        assertThat(replay.id()).isEqualTo(7L);
        assertThat(replay.refundedAmount()).isEqualByComparingTo("10.00");
        assertThatThrownBy(() -> service.refund(7L, new OrderRefundDto(other, new BigDecimal("40.01"), "Return #2")))
                .isInstanceOf(CustomException.class)
                .hasMessage("Refund of 40.01 exceeds the refundable amount of 40.00");

        verifyNoInteractions(commerceEvents);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void unknownOrdersAreNotFound() {
        assertThatThrownBy(() -> service.refund(8L, new OrderRefundDto(REQUEST, BigDecimal.ONE, "Return #1")))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Order not found");
                });
        verifyNoInteractions(commerceEvents);
    }
}
