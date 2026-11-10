package com.awesome.testing.internal.service;

import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.refund.OrderRefundEntity;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.internal.dto.OrderRefundDto;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.refund.OrderRefundRepository;
import com.awesome.testing.service.coupon.CouponService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A refund records the VAT it contains at the order's rate. */
@ExtendWith(MockitoExtension.class)
class InternalRefundTaxTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderRefundRepository refundRepository;
    @Mock private CouponService couponService;
    @Mock private CommerceEvents commerceEvents;
    @InjectMocks private InternalRefundService service;

    @Test
    void recordsTheVatShareOfARefund() {
        OrderEntity order = order();
        order.setTaxCountry("PL");
        order.setTaxRate(new BigDecimal("23.00"));

        assertThat(refund(order, "61.50").getTaxAmount()).hasToString("11.50");
    }

    @Test
    void leavesTheVatEmptyForOrdersWithoutVat() {
        assertThat(refund(order(), "61.50").getTaxAmount()).isNull();
    }

    private OrderRefundEntity refund(OrderEntity order, String amount) {
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.refund(7L, new OrderRefundDto(UUID.randomUUID(), new BigDecimal(amount), "RMA-1"));
        ArgumentCaptor<OrderRefundEntity> saved = ArgumentCaptor.forClass(OrderRefundEntity.class);
        verify(refundRepository).save(saved.capture());
        return saved.getValue();
    }

    private static OrderEntity order() {
        return OrderEntity.builder().id(7L).username("buyer").status(OrderStatus.DELIVERED)
                .shippingAddress(new AddressEntity()).items(new ArrayList<>())
                .totalAmount(new BigDecimal("123.00")).refundedAmount(BigDecimal.ZERO).build();
    }
}
