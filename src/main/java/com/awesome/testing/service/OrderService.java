package com.awesome.testing.service;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.OrderItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryState;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    @Transactional
    public OrderDto createOrder(String username, AddressDto addressDto) {
        List<CartItemEntity> cartItems = cartItemRepository.findByUsernameForUpdate(username);
        if (cartItems.isEmpty()) {
            throw new CustomException("Cart is empty", HttpStatus.BAD_REQUEST);
        }

        OrderEntity order = getInitialEmptyOrder(username, addressDto);
        order.setInventoryState(InventoryState.DEDUCTED);
        cartItems.forEach(cartItem -> updateOrder(cartItem, order));
        Map<Long, Integer> quantities = cartItems.stream()
                .collect(Collectors.groupingBy(
                        cartItem -> cartItem.getProduct().getId(),
                        TreeMap::new,
                        Collectors.summingInt(CartItemEntity::getQuantity)));
        List<ProductEntity> locked = quantities.keySet().stream()
                .map(id -> productRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new CustomException("Product not found", HttpStatus.NOT_FOUND)))
                .toList();
        for (ProductEntity product : locked) {
            inventoryService.checkAvailable(product, quantities.get(product.getId()));
        }
        OrderEntity savedOrder = orderRepository.save(order);
        for (ProductEntity product : locked) {
            inventoryService.deduct(product, quantities.get(product.getId()), savedOrder);
        }
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
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));

        if (newStatus == OrderStatus.CANCELLED) {
            return cancelLocked(order);
        }
        order.setStatus(newStatus);
        return OrderDto.from(orderRepository.save(order));
    }

    @Transactional
    public OrderDto cancelOrder(Long orderId, String username, boolean isAdmin) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));

        if (!isAdmin && !order.getUsername().equals(username)) {
            throw new CustomException("You cannot cancel someone else's order", HttpStatus.FORBIDDEN);
        }

        return cancelLocked(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> getAllOrders(OrderStatus status, Pageable pageable) {
        Page<OrderEntity> orders = status == null ?
                orderRepository.findAllOrdersWithItems(pageable) :
                orderRepository.findAllOrdersByStatus(status, pageable);
        return orders.map(OrderDto::from);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .map(OrderDto::from)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
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

    private OrderDto cancelLocked(OrderEntity order) {
        if (!canBeCancelled(order.getStatus())) {
            throw new CustomException("Order cannot be cancelled in current status", HttpStatus.BAD_REQUEST);
        }
        if (order.getInventoryState() == InventoryState.DEDUCTED) {
            Map<Long, Integer> quantities = order.getItems().stream()
                    .collect(Collectors.groupingBy(
                            item -> item.getProduct().getId(),
                            TreeMap::new,
                            Collectors.summingInt(OrderItemEntity::getQuantity)));
            quantities.forEach((productId, quantity) -> {
                ProductEntity product = productRepository.findByIdForUpdate(productId)
                        .orElseThrow(() -> new CustomException("Product not found", HttpStatus.NOT_FOUND));
                inventoryService.restore(product, quantity, order);
            });
            order.setInventoryState(InventoryState.RESTORED);
        }
        order.setStatus(OrderStatus.CANCELLED);
        return OrderDto.from(orderRepository.save(order));
    }

}
