package com.awesome.testing.service;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.OrderItemEntity;
import com.awesome.testing.entity.OrderIdempotencyKeyEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.fulfilment.ShipmentEntity;
import com.awesome.testing.entity.fulfilment.ShipmentStatus;
import com.awesome.testing.repository.OrderIdempotencyKeyRepository;
import com.awesome.testing.repository.fulfilment.ShipmentRepository;
import com.awesome.testing.service.coupon.CouponQuote;
import com.awesome.testing.service.coupon.CouponService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final ShipmentRepository shipmentRepository;
    private final OrderIdempotencyKeyRepository idempotencyKeyRepository;
    private final Clock commerceClock;

    @Transactional
    public OrderDto createOrder(String username, AddressDto addressDto) {
        return createOrder(username, addressDto, null).order();
    }

    /**
     * Creates an order from the cart. When an idempotency key is supplied, a
     * repeat with the same address within 24 hours replays the original order.
     * The key is looked up after the cart rows are locked so a concurrent
     * duplicate waits for the first checkout and then replays it.
     */
    @Transactional
    public OrderCreation createOrder(String username, AddressDto addressDto, String idempotencyKey) {
        if (idempotencyKey != null && !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new CustomException(
                    "Idempotency-Key must be 8-64 letters, digits, hyphens or underscores", HttpStatus.BAD_REQUEST);
        }
        List<CartItemEntity> cartItems = cartItemRepository.findByUsernameForUpdate(username);
        String requestHash = idempotencyKey == null ? null : requestHash(addressDto);
        if (idempotencyKey != null) {
            idempotencyKeyRepository.deleteExpired(
                    username, idempotencyKey, LocalDateTime.now(commerceClock).minus(IDEMPOTENCY_TTL));
            Optional<OrderIdempotencyKeyEntity> prior =
                    idempotencyKeyRepository.findByUsernameAndKey(username, idempotencyKey);
            if (prior.isPresent()) {
                if (!prior.get().getRequestHash().equals(requestHash)) {
                    throw new CustomException(
                            "Idempotency-Key was already used with a different request", HttpStatus.CONFLICT);
                }
                return new OrderCreation(OrderDto.from(prior.get().getOrder()), true);
            }
        }
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
        BigDecimal subtotal = order.getTotalAmount();
        Optional<CouponQuote> quote = couponService.quoteForCheckout(username, subtotal);
        BigDecimal discount = quote.map(CouponQuote::discount).orElse(BigDecimal.ZERO);
        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(discount);
        order.setCouponCode(quote.map(q -> q.coupon().getCode()).orElse(null));
        order.setTotalAmount(subtotal.subtract(discount));
        OrderEntity savedOrder = orderRepository.save(order);
        for (ProductEntity product : locked) {
            inventoryService.deduct(product, quantities.get(product.getId()), savedOrder);
        }
        quote.ifPresent(q -> couponService.recordRedemption(q, savedOrder));
        cartItemRepository.deleteByUsername(username);
        if (idempotencyKey != null) {
            idempotencyKeyRepository.save(OrderIdempotencyKeyEntity.builder()
                    .username(username)
                    .key(idempotencyKey)
                    .requestHash(requestHash)
                    .order(savedOrder)
                    .build());
        }

        return new OrderCreation(OrderDto.from(savedOrder), false);
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
            return cancelLocked(order, false);
        }
        if (activeShipment(order).isPresent()) {
            throw new CustomException(
                    "Order is being fulfilled by the warehouse; use the warehouse endpoints", HttpStatus.CONFLICT);
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

        return cancelLocked(order, false);
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

    static String requestHash(AddressDto address) {
        String canonical = String.join("\u001f",
                Objects.toString(address.getStreet(), ""),
                Objects.toString(address.getCity(), ""),
                Objects.toString(address.getState(), ""),
                Objects.toString(address.getZipCode(), ""),
                Objects.toString(address.getCountry(), ""));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private boolean canBeCancelled(OrderStatus status) {
        return status == OrderStatus.PENDING || status == OrderStatus.PAID;
    }

    /** Cancels an order on behalf of the warehouse, which may also cancel a picked shipment. */
    @Transactional
    public void cancelForWarehouse(OrderEntity order) {
        cancelLocked(order, true);
    }

    /** Sets SHIPPED or DELIVERED as a consequence of a shipment transition. */
    @Transactional
    public void applyFulfilmentStatus(OrderEntity order, OrderStatus status) {
        order.setStatus(status);
        orderRepository.save(order);
    }

    private Optional<ShipmentEntity> activeShipment(OrderEntity order) {
        return shipmentRepository.findByOrderId(order.getId()).filter(s -> s.getStatus().isActive());
    }

    private OrderDto cancelLocked(OrderEntity order, boolean warehouseInitiated) {
        if (!canBeCancelled(order.getStatus())) {
            throw new CustomException("Order cannot be cancelled in current status", HttpStatus.BAD_REQUEST);
        }
        Optional<ShipmentEntity> shipment = activeShipment(order);
        if (shipment.isPresent()) {
            ShipmentStatus shipmentStatus = shipment.get().getStatus();
            boolean cancellable = shipmentStatus == ShipmentStatus.AWAITING_PICK
                    || (warehouseInitiated && shipmentStatus == ShipmentStatus.PICKED);
            if (!cancellable) {
                throw new CustomException(
                        "Order is already being packed and cannot be cancelled", HttpStatus.CONFLICT);
            }
            shipment.get().setStatus(ShipmentStatus.CANCELLED);
            shipment.get().setCancelledAt(LocalDateTime.now(commerceClock));
            shipmentRepository.save(shipment.get());
        }
        couponService.releaseRedemption(order);
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
