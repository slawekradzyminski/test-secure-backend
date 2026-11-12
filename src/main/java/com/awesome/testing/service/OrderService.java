package com.awesome.testing.service;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.OrderItemEntity;
import com.awesome.testing.entity.OrderIdempotencyKeyEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.service.checkout.CheckoutContext;
import com.awesome.testing.service.checkout.CheckoutContributors;
import com.awesome.testing.repository.OrderIdempotencyKeyRepository;
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
    private final OrderIdempotencyKeyRepository idempotencyKeyRepository;
    private final Clock commerceClock;
    private final CheckoutContributors checkoutContributors;
    private final CommerceEvents commerceEvents;

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
        checkoutContributors.contribute(new CheckoutContext(username, addressDto, cartItems, order));
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
        commerceEvents.publish(CommerceEventType.ORDER_CREATED, savedOrder);

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
            return cancelLocked(order);
        }
        if (fulfilmentOf(order).blocksManualStatusChange()) {
            throw new CustomException(
                    "Order is being fulfilled by the warehouse; use the warehouse endpoints", HttpStatus.CONFLICT);
        }
        OrderStatus previous = order.getStatus();
        if (previous == newStatus) {
            return OrderDto.from(order);
        }
        if (!previous.canMoveTo(newStatus)) {
            throw new CustomException(
                    "Cannot change order status from " + previous + " to " + newStatus, HttpStatus.BAD_REQUEST);
        }
        order.setStatus(newStatus);
        OrderEntity saved = orderRepository.save(order);
        CommerceEventType.reaching(newStatus).ifPresent(type -> commerceEvents.publish(type, saved));
        return OrderDto.from(saved);
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

    /**
     * Cancels an order whose shipment warehouse-service cancels. Allowed only
     * for PAID orders that are waiting for or have finished picking.
     */
    @Transactional
    public OrderEntity cancelForWarehouse(Long orderId) {
        OrderEntity order = lockOrder(orderId);
        if (order.getStatus() != OrderStatus.PAID || !fulfilmentOf(order).allowsWarehouseCancellation()) {
            throw new CustomException("Order cannot be cancelled by the warehouse in status " + order.getStatus()
                    + " with fulfilment state " + fulfilmentOf(order), HttpStatus.CONFLICT);
        }
        return releaseAndCancel(order);
    }

    /**
     * Mirrors a shipment transition from warehouse-service onto the order.
     * Only the next forward step is accepted; repeating the current state is a
     * no-op so a retried call is safe. That includes AWAITING_PICK: a release
     * whose response was lost can be retried; warehouse-service prevents
     * duplicate shipments with its unique order constraint.
     */
    @Transactional
    public OrderEntity applyFulfilmentState(Long orderId, FulfilmentState target) {
        if (target == null || target == FulfilmentState.NONE || target == FulfilmentState.CANCELLED) {
            throw new CustomException(
                    "Fulfilment state must be one of AWAITING_PICK, PICKED, PACKED, SHIPPED, DELIVERED; "
                            + "use warehouse-cancel to cancel", HttpStatus.BAD_REQUEST);
        }
        OrderEntity order = lockOrder(orderId);
        FulfilmentState current = fulfilmentOf(order);
        if (current == target) {
            return order;
        }
        if (current.nextStep() != target) {
            throw new CustomException(
                    "Cannot change fulfilment state from " + current + " to " + target, HttpStatus.CONFLICT);
        }
        if (target == FulfilmentState.AWAITING_PICK && order.getStatus() != OrderStatus.PAID) {
            throw new CustomException(
                    "Only PAID orders can be released to the warehouse; order is " + order.getStatus(),
                    HttpStatus.CONFLICT);
        }
        order.setFulfilmentState(target);
        OrderStatus implied = target.impliedOrderStatus();
        if (implied != null) {
            order.setStatus(implied);
        }
        OrderEntity saved = orderRepository.save(order);
        if (implied != null) {
            CommerceEventType.reaching(implied).ifPresent(type -> commerceEvents.publish(type, saved));
        }
        return saved;
    }

    private OrderEntity lockOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
    }

    private static FulfilmentState fulfilmentOf(OrderEntity order) {
        return FulfilmentState.orNone(order.getFulfilmentState());
    }

    private OrderDto cancelLocked(OrderEntity order) {
        if (!canBeCancelled(order.getStatus())) {
            throw new CustomException("Order cannot be cancelled in current status", HttpStatus.BAD_REQUEST);
        }
        if (!fulfilmentOf(order).allowsCustomerCancellation()) {
            throw new CustomException(
                    "Order is already being packed and cannot be cancelled", HttpStatus.CONFLICT);
        }
        return OrderDto.from(releaseAndCancel(order));
    }

    private OrderEntity releaseAndCancel(OrderEntity order) {
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
        // Only orders the warehouse already holds get a CANCELLED fulfilment state;
        // untouched orders stay NONE so their public body is unchanged.
        if (fulfilmentOf(order) != FulfilmentState.NONE) {
            order.setFulfilmentState(FulfilmentState.CANCELLED);
        }
        OrderEntity saved = orderRepository.save(order);
        commerceEvents.publish(CommerceEventType.ORDER_CANCELLED, saved);
        return saved;
    }

}
