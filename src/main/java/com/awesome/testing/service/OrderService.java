package com.awesome.testing.service;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.*;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional
    public OrderDto createOrder(String username, AddressDto addressDto) {
        List<CartItemEntity> cartItems = cartItemRepository.findByUsername(username);
        if (cartItems.isEmpty()) {
            throw new CustomException("Cart is empty", HttpStatus.BAD_REQUEST);
        }

        OrderEntity order = getInitialEmptyOrder(username, addressDto);
        cartItems.forEach(cartItem -> updateOrder(cartItem, order));
        OrderEntity savedOrder = orderRepository.save(order);
        cartItemRepository.deleteByUsername(username);

        return OrderDto.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> getUserOrders(String username, OrderStatus status, Pageable pageable) {
        Page<OrderEntity> orders = status == null ?
                orderRepository.findByUsername(username, pageable) :
                orderRepository.findByUsernameAndStatus(username, status, pageable);
        return orders.map(OrderDto::from);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(String username, Long orderId) {
        return orderRepository.findByIdAndUsername(orderId, username)
                .map(OrderDto::from)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));

        if (newStatus == OrderStatus.CANCELLED && !canBeCancelled(order.getStatus())) {
            throw new CustomException("Order cannot be cancelled in current status", HttpStatus.BAD_REQUEST);
        }

        order.setStatus(newStatus);
        return OrderDto.from(orderRepository.save(order));
    }

    private OrderEntity getInitialEmptyOrder(String username, AddressDto addressDto) {
        return OrderEntity.builder()
                .username(username)
                .status(OrderStatus.PENDING)
                .shippingAddress(AddressEntity.from(addressDto))
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    private void updateOrder(CartItemEntity cartItem, OrderEntity order) {
        OrderItemEntity orderItem = OrderItemEntity.from(cartItem);
        order.addItem(orderItem);
        order.setTotalAmount(order.getTotalAmount().add(
                cartItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()))
        ));
    }

    private boolean canBeCancelled(OrderStatus status) {
        return status == OrderStatus.PENDING || status == OrderStatus.PAID;
    }

}