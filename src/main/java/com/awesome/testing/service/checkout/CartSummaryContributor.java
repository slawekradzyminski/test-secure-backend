package com.awesome.testing.service.checkout;

/**
 * Adds optional fields to the cart summary returned by {@code GET /api/v1/cart}
 * and the other cart endpoints. Contributors are called in
 * {@link org.springframework.core.annotation.Order @Order} after the coupon
 * preview is applied.
 *
 * <p>A field a contributor sets must be declared on
 * {@link com.awesome.testing.dto.cart.CartDto} with
 * {@code @JsonInclude(JsonInclude.Include.NON_NULL)} and set only when it
 * applies, so carts it does not concern keep their exact legacy JSON body.
 */
@FunctionalInterface
public interface CartSummaryContributor {

    void contribute(CartSummaryContext context);
}
