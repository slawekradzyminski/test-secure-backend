package com.awesome.testing.service.checkout;

import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.entity.CartItemEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a {@link CartSummaryContributor} sees.
 *
 * @param username  the cart owner
 * @param cartItems the cart rows (read-only list)
 * @param subtotal  sum of price times quantity
 * @param discount  coupon discount checkout would apply, zero without a coupon
 * @param cart      the summary being built; set only fields that apply
 */
public record CartSummaryContext(
        String username, List<CartItemEntity> cartItems, BigDecimal subtotal, BigDecimal discount, CartDto cart) {

    public CartSummaryContext {
        cartItems = List.copyOf(cartItems);
    }
}
