package com.awesome.testing.service;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderItemDto;
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

        OrderEntity order = OrderEntity.builder()
                .username(username)
                .status(OrderStatus.PENDING)
                .shippingAddress(mapToAddress(addressDto))
                .totalAmount(BigDecimal.ZERO)
                .build();

        cartItems.forEach(cartItem -> updateOrder(cartItem, order));

        OrderEntity savedOrder = orderRepository.save(order);
        cartItemRepository.deleteByUsername(username);

        return mapToOrderDto(savedOrder);
    }

    private void updateOrder(CartItemEntity cartItem, OrderEntity order) {
        OrderItemEntity orderItem = OrderItemEntity.builder()
                .product(cartItem.getProduct())
                .quantity(cartItem.getQuantity())
                .price(cartItem.getPrice())
                .build();
        order.addItem(orderItem);
        order.setTotalAmount(order.getTotalAmount().add(
                cartItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()))
        ));
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> getUserOrders(String username, OrderStatus status, Pageable pageable) {
        Page<OrderEntity> orders = status == null ?
                orderRepository.findByUsername(username, pageable) :
                orderRepository.findByUsernameAndStatus(username, status, pageable);
        return orders.map(this::mapToOrderDto);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(String username, Long orderId) {
        return orderRepository.findByIdAndUsername(orderId, username)
                .map(this::mapToOrderDto)
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
        return mapToOrderDto(orderRepository.save(order));
    }

    private boolean canBeCancelled(OrderStatus status) {
        return status == OrderStatus.PENDING || status == OrderStatus.PAID;
    }

    private AddressEntity mapToAddress(AddressDto dto) {
        return AddressEntity.builder()
                .street(dto.getStreet())
                .city(dto.getCity())
                .state(dto.getState())
                .zipCode(dto.getZipCode())
                .country(dto.getCountry())
                .build();
    }

    private OrderDto mapToOrderDto(OrderEntity order) {
        return OrderDto.builder()
                .id(order.getId())
                .username(order.getUsername())
                .items(mapToOrderItemDtos(order.getItems()))
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .shippingAddress(mapToAddressDto(order.getShippingAddress()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private List<OrderItemDto> mapToOrderItemDtos(List<OrderItemEntity> items) {
        return items.stream()
                .map(item -> OrderItemDto.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getPrice())
                        .totalPrice(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();
    }

    private AddressDto mapToAddressDto(AddressEntity address) {
        return AddressDto.builder()
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .zipCode(address.getZipCode())
                .country(address.getCountry())
                .build();
    }
} 