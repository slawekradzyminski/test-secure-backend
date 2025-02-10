package com.awesome.testing.factory;

import com.awesome.testing.dto.cart.CartItemDto;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CartItemFactory {

    public static CartItemDto getSingleCartItemFrom(Long productId) {
        return CartItemDto.builder()
                .productId(productId)
                .quantity(1)
                .build();
    }

    public static CartItemDto getDoubleCartItemFrom(Long productId) {
        return CartItemDto.builder()
                .productId(productId)
                .quantity(2)
                .build();
    }

}
