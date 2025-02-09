package com.awesome.testing.factory;

import com.awesome.testing.dto.ProductCreateDto;
import com.awesome.testing.model.ProductEntity;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

@UtilityClass
public class ProductFactory {

    public static ProductEntity getRandomProduct() {
        return ProductEntity.builder()
                .name("Test Product")
                .description("Test Description")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(10)
                .category("Test Category")
                .build();
    }

    public static ProductCreateDto getRandomProductCreate() {
        return ProductCreateDto.builder()
                .name("Test Product")
                .description("Test Description")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(10)
                .category("Test Category")
                .build();
    }

}
