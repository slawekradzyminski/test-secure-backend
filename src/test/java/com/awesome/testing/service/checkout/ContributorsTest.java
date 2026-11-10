package com.awesome.testing.service.checkout;

import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.OrderEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contributor beans run in @Order and see the context they were given. */
class ContributorsTest {

    static final List<String> CALLS = new ArrayList<>();

    @Configuration
    static class Beans {
        @Bean
        @Order(2)
        CheckoutContributor second() {
            return context -> CALLS.add("checkout-2:" + context.username());
        }

        @Bean
        @Order(1)
        CheckoutContributor first() {
            return context -> CALLS.add("checkout-1:" + context.order().getTotalAmount());
        }

        @Bean
        @Order(5)
        CartSummaryContributor laterCart() {
            return context -> CALLS.add("cart-5");
        }

        @Bean
        @Order(-1)
        CartSummaryContributor earlierCart() {
            return context -> CALLS.add("cart--1:" + context.subtotal());
        }

        @Bean
        CheckoutContributors checkoutContributors(ObjectProvider<CheckoutContributor> all) {
            return new CheckoutContributors(all);
        }

        @Bean
        CartSummaryContributors cartSummaryContributors(ObjectProvider<CartSummaryContributor> all) {
            return new CartSummaryContributors(all);
        }
    }

    @Test
    void beansAreCalledInOrder() {
        CALLS.clear();
        try (var context = new AnnotationConfigApplicationContext(Beans.class)) {
            context.getBean(CheckoutContributors.class).contribute(new CheckoutContext(
                    "buyer", null, List.of(), OrderEntity.builder().totalAmount(BigDecimal.TEN).build()));
            context.getBean(CartSummaryContributors.class).contribute(new CartSummaryContext(
                    "buyer", List.of(), BigDecimal.ONE, BigDecimal.ZERO, new CartDto()));
        }

        assertThat(CALLS).containsExactly("checkout-1:10", "checkout-2:buyer", "cart--1:1", "cart-5");
    }

    @Test
    void withoutBeansNothingHappens() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CheckoutContributors.class);
            context.registerBean(CartSummaryContributors.class);
            context.refresh();
            CartDto cart = new CartDto();
            context.getBean(CartSummaryContributors.class)
                    .contribute(new CartSummaryContext("buyer", List.of(), BigDecimal.ONE, BigDecimal.ZERO, cart));
            context.getBean(CheckoutContributors.class)
                    .contribute(new CheckoutContext("buyer", null, List.of(), new OrderEntity()));

            assertThat(cart).isEqualTo(new CartDto());
        }
        CheckoutContributors.none().contribute(new CheckoutContext("buyer", null, List.of(), new OrderEntity()));
        CartSummaryContributors.none().contribute(
                new CartSummaryContext("buyer", List.of(), BigDecimal.ONE, BigDecimal.ZERO, new CartDto()));
    }

    @Test
    void contextsExposeReadOnlyCartItems() {
        List<CartItemEntity> items = new ArrayList<>(List.of(new CartItemEntity()));
        CheckoutContext checkout = new CheckoutContext("buyer", null, items, new OrderEntity());
        CartSummaryContext cart = new CartSummaryContext("buyer", items, BigDecimal.ONE, BigDecimal.ZERO, new CartDto());
        items.clear();

        assertThat(checkout.cartItems()).hasSize(1);
        assertThat(cart.cartItems()).hasSize(1);
        assertThatThrownBy(() -> checkout.cartItems().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> cart.cartItems().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
