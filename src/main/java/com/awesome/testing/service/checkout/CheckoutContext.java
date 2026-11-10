package com.awesome.testing.service.checkout;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.OrderEntity;

import java.util.List;

/**
 * What a {@link CheckoutContributor} sees during checkout.
 *
 * @param username  the buyer
 * @param address   the shipping address from the request
 * @param cartItems the locked cart rows being ordered (read-only list)
 * @param order     the unsaved order with items, {@code subtotalAmount}, {@code discountAmount},
 *                  {@code couponCode} and {@code totalAmount} already set
 */
public record CheckoutContext(String username, AddressDto address, List<CartItemEntity> cartItems, OrderEntity order) {

    public CheckoutContext {
        cartItems = List.copyOf(cartItems);
    }
}
