package com.awesome.testing.internal.dto;

import com.awesome.testing.entity.ProductEntity;

import java.math.BigDecimal;

public record InternalProductDto(Long id, String name, String category, BigDecimal price, Integer stockQuantity) {

    public static InternalProductDto from(ProductEntity product) {
        return new InternalProductDto(
                product.getId(), product.getName(), product.getCategory(), product.getPrice(), product.getStockQuantity());
    }
}
