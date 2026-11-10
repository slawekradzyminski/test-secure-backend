package com.awesome.testing.service.checkout;

/**
 * Extends checkout. {@code OrderService.createOrder} calls every contributor
 * bean, ordered by {@link org.springframework.core.annotation.Order @Order} or
 * {@link org.springframework.core.Ordered}, after the items and coupon are
 * priced and before the order is saved. A contributor may set its own fields
 * on {@link CheckoutContext#order()} or reject checkout by throwing a
 * {@link com.awesome.testing.controller.exception.CustomException}; the whole
 * checkout then rolls back and the cart is kept.
 *
 * <p>New orders must stay {@code PENDING} and keep
 * {@code totalAmount = subtotalAmount - discountAmount}.
 */
@FunctionalInterface
public interface CheckoutContributor {

    void contribute(CheckoutContext context);
}
