package com.awesome.testing.factory;

import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.dto.product.ProductUpdateDto;
import com.awesome.testing.model.ProductEntity;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

@UtilityClass
public class ProductFactory extends FakerSingleton {

    public static ProductEntity getRandomProduct() {
        return ProductEntity.builder()
                .name(FAKER.commerce().productName())
                .description(FAKER.lorem().sentence())
                .price(generateValidPrice())
                .stockQuantity(FAKER.random().nextInt(1, 10000))
                .category(FAKER.commerce().department())
                .build();
    }

    public static ProductCreateDto getRandomProductCreate() {
        return ProductCreateDto.builder()
                .name(FAKER.commerce().productName())
                .description(FAKER.lorem().sentence())
                .price(generateValidPrice())
                .stockQuantity(FAKER.random().nextInt(1, 10000))
                .category(FAKER.commerce().department())
                .build();
    }

    public static ProductUpdateDto getRandomProductUpdate() {
        return ProductUpdateDto.builder()
                .name(FAKER.commerce().productName())
                .description(FAKER.lorem().sentence())
                .price(generateValidPrice())
                .stockQuantity(FAKER.random().nextInt(1, 10000))
                .category(FAKER.commerce().department())
                .build();
    }

    private static BigDecimal generateValidPrice() {
        String priceStr = FAKER.commerce().price(0.01, 999999.99);
        return new BigDecimal(priceStr).setScale(2, RoundingMode.HALF_UP);
    }

}
