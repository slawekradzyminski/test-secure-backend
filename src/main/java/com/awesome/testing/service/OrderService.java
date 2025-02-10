package com.awesome.testing.service;

import com.awesome.testing.dto.AddressDTO;
import com.awesome.testing.dto.OrderDTO;
import com.awesome.testing.dto.OrderItemDTO;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.model.*;
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
    public OrderDTO createOrder(String username, AddressDTO addressDTO) {
        List<CartItemEntity> cartItems = cartItemRepository.findByUsername(username);
        if (cartItems.isEmpty()) {
            throw new CustomException("Cart is empty", HttpStatus.BAD_REQUEST);
        }

        Order order = Order.builder()
                .username(username)
                .status(OrderStatus.PENDING)
                .shippingAddress(mapToAddress(addressDTO))
                .totalAmount(BigDecimal.ZERO)
                .build();

        cartItems.forEach(cartItem -> updateOrder(cartItem, order));

        Order savedOrder = orderRepository.save(order);
        cartItemRepository.deleteByUsername(username);

        return mapToOrderDTO(savedOrder);
    }

    private void updateOrder(CartItemEntity cartItem, Order order) {
        OrderItem orderItem = OrderItem.builder()
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
    public Page<OrderDTO> getUserOrders(String username, OrderStatus status, Pageable pageable) {
        Page<Order> orders = status == null ?
                orderRepository.findByUsername(username, pageable) :
                orderRepository.findByUsernameAndStatus(username, status, pageable);
        return orders.map(this::mapToOrderDTO);
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrder(String username, Long orderId) {
        return orderRepository.findByIdAndUsername(orderId, username)
                .map(this::mapToOrderDTO)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public OrderDTO updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));

        if (newStatus == OrderStatus.CANCELLED && !canBeCancelled(order.getStatus())) {
            throw new CustomException("Order cannot be cancelled in current status", HttpStatus.BAD_REQUEST);
        }

        order.setStatus(newStatus);
        return mapToOrderDTO(orderRepository.save(order));
    }

    private boolean canBeCancelled(OrderStatus status) {
        return status == OrderStatus.PENDING || status == OrderStatus.PAID;
    }

    private Address mapToAddress(AddressDTO dto) {
        return Address.builder()
                .street(dto.getStreet())
                .city(dto.getCity())
                .state(dto.getState())
                .zipCode(dto.getZipCode())
                .country(dto.getCountry())
                .build();
    }

    private OrderDTO mapToOrderDTO(Order order) {
        return OrderDTO.builder()
                .id(order.getId())
                .username(order.getUsername())
                .items(mapToOrderItemDTOs(order.getItems()))
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .shippingAddress(mapToAddressDTO(order.getShippingAddress()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private List<OrderItemDTO> mapToOrderItemDTOs(List<OrderItem> items) {
        return items.stream()
                .map(item -> OrderItemDTO.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getPrice())
                        .totalPrice(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();
    }

    private AddressDTO mapToAddressDTO(Address address) {
        return AddressDTO.builder()
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .zipCode(address.getZipCode())
                .country(address.getCountry())
                .build();
    }
} 