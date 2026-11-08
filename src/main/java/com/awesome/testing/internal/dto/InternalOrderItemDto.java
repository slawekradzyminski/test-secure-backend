package com.awesome.testing.internal.dto;

import com.awesome.testing.entity.OrderItemEntity;

import java.math.BigDecimal;

public record InternalOrderItemDto(
        Long productId, String productName, String category, Integer quantity, BigDecimal unitPrice) {

    public static InternalOrderItemDto from(OrderItemEntity item) {
        return new InternalOrderItemDto(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getCategory(),
                item.getQuantity(),
                item.getPrice());
    }
}
