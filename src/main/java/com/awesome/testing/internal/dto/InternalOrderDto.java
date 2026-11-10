package com.awesome.testing.internal.dto;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.service.checkout.OrderAmounts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Order snapshot for warehouse-service and payment-service; {@code amountDue} is what a card payment must cover. */
public record InternalOrderDto(
        Long id,
        String username,
        OrderStatus status,
        FulfilmentState fulfilmentState,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String couponCode,
        BigDecimal refundedAmount,
        BigDecimal amountDue,
        AddressDto shippingAddress,
        List<InternalOrderItemDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static InternalOrderDto from(OrderEntity order) {
        return new InternalOrderDto(
                order.getId(),
                order.getUsername(),
                order.getStatus(),
                FulfilmentState.orNone(order.getFulfilmentState()),
                order.getSubtotalAmount() == null ? order.getTotalAmount() : order.getSubtotalAmount(),
                order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getCouponCode(),
                order.getRefundedAmount() == null ? BigDecimal.ZERO : order.getRefundedAmount(),
                OrderAmounts.amountDue(order),
                AddressDto.from(order.getShippingAddress()),
                order.getItems().stream().map(InternalOrderItemDto::from).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
