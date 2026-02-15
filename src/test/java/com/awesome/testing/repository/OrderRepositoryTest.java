package com.awesome.testing.repository;

import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    private ProductEntity product;

    @BeforeEach
    void setUp() {
        product = entityManager.persist(ProductEntity.builder()
                .name("Laptop")
                .description("Gaming")
                .price(BigDecimal.valueOf(1200))
                .stockQuantity(5)
                .category("Electronics")
                .build());
    }

    @Test
    void shouldFindOrdersByUsername() {
        persistOrder("john", OrderStatus.PENDING);
        persistOrder("john", OrderStatus.CANCELLED);

        Page<OrderEntity> page = orderRepository.findByUsername("john", PageRequest.of(0, 5));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(order -> order.getItems().size() == 1);
    }

    @Test
    void shouldFilterOrdersByStatus() {
        persistOrder("john", OrderStatus.PENDING);
        persistOrder("john", OrderStatus.SHIPPED);

        Page<OrderEntity> page = orderRepository.findByUsernameAndStatus(
                "john", OrderStatus.PENDING, PageRequest.of(0, 5));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void shouldFindByIdAndUsername() {
        OrderEntity order = persistOrder("owner", OrderStatus.PENDING);

        assertThat(orderRepository.findByIdAndUsername(order.getId(), "owner")).isPresent();
        assertThat(orderRepository.findByIdAndUsername(order.getId(), "other")).isEmpty();
    }

    @Test
    void shouldFindAllOrdersByStatus() {
        persistOrder("a", OrderStatus.PENDING);
        persistOrder("b", OrderStatus.PENDING);
        persistOrder("c", OrderStatus.CANCELLED);

        Page<OrderEntity> page = orderRepository.findAllOrdersByStatus(
                OrderStatus.PENDING, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    private OrderEntity persistOrder(String username, OrderStatus status) {
        OrderEntity order = OrderEntity.builder()
                .username(username)
                .status(status)
                .shippingAddress(AddressEntity.builder()
                        .street("Street")
                        .city("City")
                        .state("State")
                        .zipCode("12-345")
                        .country("PL")
                        .build())
                .totalAmount(BigDecimal.ZERO)
                .build();
        OrderItemEntity item = OrderItemEntity.builder()
                .product(product)
                .quantity(1)
                .price(product.getPrice())
                .build();
        order.addItem(item);
        order.setTotalAmount(product.getPrice());
        return entityManager.persist(order);
    }
}
