package com.awesome.testing.fakedata;

import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.*;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SetupOrders {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final SetupUsers setupUsers;
    private final SetupProducts setupProducts;

    @Transactional
    public void createOrdersAndCart() {
        if (orderRepository.count() > 0) {
            return;
        }

        UserEntity client = setupUsers.getClientUser();
        UserEntity client2 = setupUsers.getClient2User();

        ProductEntity iphone = setupProducts.getIPhone();
        ProductEntity macbook = setupProducts.getMacBook();
        ProductEntity ps5 = setupProducts.getPlayStation();
        ProductEntity watch = setupProducts.getAppleWatch();
        ProductEntity headphones = setupProducts.getSonyHeadphones();

        // Create orders for client1 in different statuses
        createOrder(client, iphone, 1, OrderStatus.DELIVERED, LocalDateTime.now().minusDays(7));
        createOrder(client, macbook, 1, OrderStatus.SHIPPED, LocalDateTime.now().minusDays(2));
        createOrder(client, ps5, 1, OrderStatus.PENDING, LocalDateTime.now().minusHours(2));

        // Create one order for client2
        createOrder(client2, iphone, 1, OrderStatus.PAID, LocalDateTime.now().minusDays(1));

        // Add items to client2's cart
        createCartItem(client2, watch, 1);
        createCartItem(client2, headphones, 2);
    }

    private void createOrder(UserEntity user, ProductEntity product, int quantity, OrderStatus status, LocalDateTime createdAt) {
        BigDecimal totalPrice = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        
        OrderItemEntity orderItem = new OrderItemEntity();
        orderItem.setProduct(product);
        orderItem.setQuantity(quantity);
        orderItem.setPrice(product.getPrice());

        OrderEntity order = new OrderEntity();
        order.setUsername(user.getUsername());
        order.setItems(List.of(orderItem));
        order.setTotalAmount(totalPrice);
        order.setStatus(status);
        order.setShippingAddress(createAddress());
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(createdAt);

        orderItem.setOrder(order);
        orderRepository.save(order);
    }

    private void createCartItem(UserEntity user, ProductEntity product, int quantity) {
        CartItemEntity cartItem = new CartItemEntity();
        cartItem.setUsername(user.getUsername());
        cartItem.setProduct(product);
        cartItem.setQuantity(quantity);
        cartItem.setPrice(product.getPrice());
        cartItem.setVersion(0L);

        cartItemRepository.save(cartItem);
    }

    private AddressEntity createAddress() {
        return AddressEntity.builder()
                .street("123 Main St")
                .city("New York")
                .state("NY")
                .zipCode("10001")
                .country("USA")
                .build();
    }
} 