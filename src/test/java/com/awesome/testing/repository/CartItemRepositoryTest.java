package com.awesome.testing.repository;

import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.ProductEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CartItemRepositoryTest {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    private ProductEntity baseProduct;

    @BeforeEach
    void setUp() {
        baseProduct = createProduct("Phone");
    }

    @Test
    void shouldFindItemsByUsername() {
        persistCartItem("john", 2);
        persistCartItem("john", 1);

        List<CartItemEntity> items = cartItemRepository.findByUsername("john");

        assertThat(items).hasSize(2);
        assertThat(items)
                .allMatch(item -> item.getProduct() != null && item.getProduct().getId() != null);
    }

    @Test
    void shouldFindItemByUsernameAndProduct() {
        CartItemEntity item = persistCartItem("john", 1, baseProduct);

        Optional<CartItemEntity> found = cartItemRepository.findByUsernameAndProductId(
                "john", item.getProduct().getId());

        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(1);
    }

    @Test
    void shouldDeleteItemsByUsername() {
        persistCartItem("john", 1);
        persistCartItem("john", 2);

        cartItemRepository.deleteByUsername("john");
        entityManager.flush();

        assertThat(cartItemRepository.findByUsername("john")).isEmpty();
    }

    @Test
    void shouldDeleteItemByUsernameAndProduct() {
        CartItemEntity item = persistCartItem("john", 1, baseProduct);
        persistCartItem("john", 3);

        cartItemRepository.deleteByUsernameAndProductId("john", item.getProduct().getId());
        entityManager.flush();

        List<CartItemEntity> remaining = cartItemRepository.findByUsername("john");
        assertThat(remaining).hasSize(1);
    }

    private CartItemEntity persistCartItem(String username, int qty) {
        return persistCartItem(username, qty, createProduct("Product-" + System.nanoTime()));
    }

    private CartItemEntity persistCartItem(String username, int qty, ProductEntity product) {
        CartItemEntity entity = CartItemEntity.builder()
                .username(username)
                .product(product)
                .quantity(qty)
                .price(product.getPrice())
                .build();
        return entityManager.persist(entity);
    }

    private ProductEntity createProduct(String name) {
        return entityManager.persist(ProductEntity.builder()
                .name(name)
                .description("Smartphone")
                .price(BigDecimal.valueOf(900))
                .stockQuantity(10)
                .category("Electronics")
                .build());
    }
}
