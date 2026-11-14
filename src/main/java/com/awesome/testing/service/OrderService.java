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
import com.awesome.testing.dto.order.UtcMillis;
import com.awesome.testing.event.CancellationReason;
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
import com.awesome.testing.service.reservation.OrderReservations;
import com.awesome.testing.dto.stock.ReservationDto;
import com.awesome.testing.dto.stock.ReservedLineDto;
import com.awesome.testing.dto.stock.StockLineDto;
import com.awesome.testing.service.stock.StockLedgerClient;
import com.awesome.testing.service.stock.StockLedgerRejection;
import com.awesome.testing.service.stock.StockProjection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Orders: checkout, cancellation, status and fulfilment changes.
 *
 * <p>Stock lives in warehouse-service's ledger (docs/ARCHITECTURE_DECISIONS.md ADR 6). Checkout is a
 * small saga that never holds a database connection while it calls warehouse-service (the pool has
 * 3 connections in production): a read-only transaction validates the cart, the reservation is made
 * with no transaction open, a write transaction stores the order with the reservation id and applies
 * the reserved levels to the {@code products.stock_quantity} projection, and the reservation is then
 * confirmed. A failed write releases the reservation. Cancellations no longer touch stock: the
 * {@code order.cancelled} event carries {@code stockRestore}, which warehouse-service applies.
 */
@Slf4j
@Service
public class OrderService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    /** Checkout attempts when the cart changes between the read and the write transaction. */
    static final int MAX_CHECKOUT_ATTEMPTS = 3;

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final OrderIdempotencyKeyRepository idempotencyKeyRepository;
    private final Clock commerceClock;
    private final CheckoutContributors checkoutContributors;
    private final CommerceEvents commerceEvents;
    private final OrderReservations orderReservations;
    private final StockLedgerClient stockLedger;
    private final StockProjection stockProjection;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public OrderService(OrderRepository orderRepository,
                        CartItemRepository cartItemRepository,
                        ProductRepository productRepository,
                        InventoryService inventoryService,
                        CouponService couponService,
                        OrderIdempotencyKeyRepository idempotencyKeyRepository,
                        Clock commerceClock,
                        CheckoutContributors checkoutContributors,
                        CommerceEvents commerceEvents,
                        OrderReservations orderReservations,
                        StockLedgerClient stockLedger,
                        StockProjection stockProjection,
                        PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.couponService = couponService;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.commerceClock = commerceClock;
        this.checkoutContributors = checkoutContributors;
        this.commerceEvents = commerceEvents;
        this.orderReservations = orderReservations;
        this.stockLedger = stockLedger;
        this.stockProjection = stockProjection;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    public OrderDto createOrder(String username, AddressDto addressDto) {
        return createOrder(username, addressDto, null).order();
    }

    /**
     * Creates an order from the cart (contract section 4.1). When an idempotency key is supplied, a
     * repeat with the same address within 24 hours replays the original order. The key is checked
     * again after the cart rows are locked, so a concurrent duplicate waits for the first checkout,
     * replays it and releases its own reservation.
     *
     * <p>Must be called with no transaction open: the reservation is an HTTP call.
     */
    public OrderCreation createOrder(String username, AddressDto addressDto, String idempotencyKey) {
        if (idempotencyKey != null && !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new CustomException(
                    "Idempotency-Key must be 8-64 letters, digits, hyphens or underscores", HttpStatus.BAD_REQUEST);
        }
        for (int attempt = 1; ; attempt++) {
            Optional<OrderCreation> created = attemptCheckout(username, addressDto, idempotencyKey);
            if (created.isPresent()) {
                return created.get();
            }
            if (attempt >= MAX_CHECKOUT_ATTEMPTS) {
                throw new CustomException("Cart changed during checkout; please try again", HttpStatus.CONFLICT);
            }
        }
    }

    /** One pass of the saga; empty when the cart changed between the read and the write transaction. */
    private Optional<OrderCreation> attemptCheckout(String username, AddressDto addressDto, String idempotencyKey) {
        CheckoutPlan plan = readTransaction.execute(status -> prepareCheckout(username, addressDto, idempotencyKey));
        if (plan.replay() != null) {
            return Optional.of(plan.replay());
        }
        UUID reservationId = UUID.randomUUID();
        ReservationDto reservation = reserve(reservationId, username, plan.quantities());
        Placement placement;
        try {
            placement = writeTransaction.execute(status ->
                    placeOrder(username, addressDto, idempotencyKey, plan, reservation));
        } catch (CartChangedDuringCheckout changed) {
            releaseQuietly(reservationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            releaseQuietly(reservationId);
            throw e;
        }
        if (placement.creation().replayed()) {
            releaseQuietly(reservationId);
        } else {
            confirmQuietly(reservationId, placement.orderId());
        }
        return Optional.of(placement.creation());
    }

    /** The read-only step of checkout: what to reserve, or the order an idempotency key replays. */
    record CheckoutPlan(OrderCreation replay, SortedMap<Long, Integer> quantities) {
    }

    /** The write step's outcome: the new (or replayed) order. */
    record Placement(OrderCreation creation, Long orderId) {
    }

    /** The cart changed between the read and the write transaction; checkout starts again. */
    static final class CartChangedDuringCheckout extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        CartChangedDuringCheckout() {
            super("Cart changed during checkout", null, false, false);
        }
    }

    private CheckoutPlan prepareCheckout(String username, AddressDto addressDto, String idempotencyKey) {
        List<CartItemEntity> cartItems = cartItemRepository.findByUsername(username);
        if (idempotencyKey != null) {
            Optional<OrderIdempotencyKeyEntity> prior =
                    idempotencyKeyRepository.findByUsernameAndKey(username, idempotencyKey)
                            .filter(key -> !isExpired(key));
            if (prior.isPresent()) {
                return new CheckoutPlan(replay(prior.get(), requestHash(addressDto)), null);
            }
        }
        if (cartItems.isEmpty()) {
            throw new CustomException("Cart is empty", HttpStatus.BAD_REQUEST);
        }
        SortedMap<Long, Integer> quantities = quantitiesOf(cartItems);
        Map<Long, ProductEntity> products = new TreeMap<>();
        cartItems.forEach(item -> products.putIfAbsent(item.getProduct().getId(), item.getProduct()));
        // Fast 409 against the projection; warehouse-service decides when the reservation is made.
        products.forEach((id, product) -> inventoryService.checkAvailable(product, quantities.get(id)));
        return new CheckoutPlan(null, quantities);
    }

    private boolean isExpired(OrderIdempotencyKeyEntity key) {
        return key.getCreatedAt() != null
                && key.getCreatedAt().isBefore(LocalDateTime.now(commerceClock).minus(IDEMPOTENCY_TTL));
    }

    private static OrderCreation replay(OrderIdempotencyKeyEntity prior, String requestHash) {
        if (!prior.getRequestHash().equals(requestHash)) {
            throw new CustomException(
                    "Idempotency-Key was already used with a different request", HttpStatus.CONFLICT);
        }
        return new OrderCreation(OrderDto.from(prior.getOrder()), true);
    }

    private ReservationDto reserve(UUID reservationId, String username, SortedMap<Long, Integer> quantities) {
        List<StockLineDto> lines = new ArrayList<>(quantities.size());
        quantities.forEach((productId, quantity) -> lines.add(new StockLineDto(productId, quantity)));
        try {
            return stockLedger.reserve(reservationId, username, lines);
        } catch (StockLedgerRejection rejection) {
            if (rejection.isNotFound()) {
                throw new CustomException("Product not found", HttpStatus.NOT_FOUND, rejection);
            }
            Long shortProduct = rejection.productId() != null ? rejection.productId() : quantities.firstKey();
            throw new CustomException("Insufficient stock for product " + shortProduct, HttpStatus.CONFLICT, rejection);
        }
    }

    private Placement placeOrder(String username, AddressDto addressDto, String idempotencyKey,
                                 CheckoutPlan plan, ReservationDto reservation) {
        List<CartItemEntity> cartItems = cartItemRepository.findByUsernameForUpdate(username);
        String requestHash = idempotencyKey == null ? null : requestHash(addressDto);
        if (idempotencyKey != null) {
            idempotencyKeyRepository.deleteExpired(
                    username, idempotencyKey, LocalDateTime.now(commerceClock).minus(IDEMPOTENCY_TTL));
            Optional<OrderIdempotencyKeyEntity> prior =
                    idempotencyKeyRepository.findByUsernameAndKey(username, idempotencyKey);
            if (prior.isPresent()) {
                return new Placement(replay(prior.get(), requestHash), null);
            }
        }
        if (cartItems.isEmpty()) {
            throw new CustomException("Cart is empty", HttpStatus.BAD_REQUEST);
        }
        if (!quantitiesOf(cartItems).equals(plan.quantities())) {
            throw new CartChangedDuringCheckout();
        }

        OrderEntity order = getInitialEmptyOrder(username, addressDto);
        order.setInventoryState(InventoryState.DEDUCTED);
        order.setStockReservationId(reservation.reservationId());
        order.setReservationExpiresAt(orderReservations.expiryFor(commerceClock.instant()));
        cartItems.forEach(cartItem -> updateOrder(cartItem, order));
        BigDecimal subtotal = order.getTotalAmount();
        Optional<CouponQuote> quote = couponService.quoteForCheckout(username, subtotal);
        BigDecimal discount = quote.map(CouponQuote::discount).orElse(BigDecimal.ZERO);
        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(discount);
        order.setCouponCode(quote.map(q -> q.coupon().getCode()).orElse(null));
        order.setTotalAmount(subtotal.subtract(discount));
        checkoutContributors.contribute(new CheckoutContext(username, addressDto, cartItems, order));
        OrderEntity savedOrder = orderRepository.save(order);
        applyReservedLevels(reservation);
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
        flushBeforeMapping();

        return new Placement(new OrderCreation(OrderDto.from(savedOrder), false), savedOrder.getId());
    }

    /** Sets the projection to the levels the reservation left, locking the products in id order. */
    private void applyReservedLevels(ReservationDto reservation) {
        if (reservation.lines() == null) {
            return;
        }
        reservation.lines().stream()
                .filter(line -> line.productId() != null && line.quantityAfter() != null && line.ledgerVersion() != null)
                .sorted(Comparator.comparing(ReservedLineDto::productId))
                .forEach(line -> productRepository.findByIdForUpdate(line.productId()).ifPresent(product ->
                        stockProjection.apply(product, line.quantityAfter(), line.ledgerVersion())));
    }

    /**
     * Step 4: ties the reservation to the order. A failure is only logged: the order exists, and
     * warehouse-service's reaper confirms a reservation whose order it finds
     * ({@code GET /internal/v1/orders/by-reservation/{id}}) instead of releasing it.
     */
    private void confirmQuietly(UUID reservationId, Long orderId) {
        try {
            stockLedger.confirm(reservationId, orderId);
        } catch (RuntimeException e) {
            log.warn("Could not confirm stock reservation {} of order {}; warehouse-service's reaper will: {}",
                    reservationId, orderId, e.getMessage());
        }
    }

    /**
     * Step 5: gives back the stock of a reservation no order was stored for. A failure is only
     * logged: warehouse-service's reaper releases unconfirmed reservations without an order.
     */
    private void releaseQuietly(UUID reservationId) {
        try {
            stockLedger.release(reservationId);
        } catch (RuntimeException e) {
            log.warn("Could not release stock reservation {}; warehouse-service's reaper will: {}",
                    reservationId, e.getMessage());
        }
    }

    private static SortedMap<Long, Integer> quantitiesOf(List<CartItemEntity> cartItems) {
        return cartItems.stream()
                .collect(Collectors.groupingBy(
                        cartItem -> cartItem.getProduct().getId(),
                        TreeMap::new,
                        Collectors.summingInt(CartItemEntity::getQuantity)));
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
            return cancelLocked(order, CancellationReason.ADMIN);
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
        flushBeforeMapping();
        return OrderDto.from(saved);
    }

    @Transactional
    public OrderDto cancelOrder(Long orderId, String username, boolean isAdmin) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));

        if (!isAdmin && !order.getUsername().equals(username)) {
            throw new CustomException("You cannot cancel someone else's order", HttpStatus.FORBIDDEN);
        }

        return cancelLocked(order, isAdmin ? CancellationReason.ADMIN : CancellationReason.CUSTOMER);
    }

    /**
     * Cancels every open (PENDING or PAID, not yet shipped) order of a user whose account is
     * being erased, through the same path as a customer or admin cancellation: reserved stock
     * is restored, the coupon, gift card and points are released, and a card-paid order gets
     * its {@code card-refund.requested} feed event. The feed rows are not tied to the order,
     * so the refund still reaches payment-service after the order rows are deleted. The
     * warehouse state is not checked: an erased customer's order can no longer ship.
     * Shipped and delivered orders are left alone and deleted with the account.
     */
    @Transactional
    public void cancelOpenOrdersForErasure(String username) {
        for (OrderEntity order : orderRepository.findByUsernameForUpdate(username)) {
            if (canBeCancelled(order.getStatus())) {
                releaseAndCancel(order, CancellationReason.ACCOUNT_ERASURE);
            }
        }
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
        return releaseAndCancel(order, CancellationReason.WAREHOUSE);
    }

    /**
     * Cancels {@code orderId} when it is still PENDING and its reservation has run out, through the same
     * path as a customer cancellation, with the actor {@code system}. The row is locked with
     * {@code FOR UPDATE SKIP LOCKED}: an order another transaction holds (a capture, a customer cancel or
     * another backend instance running the job) is skipped now and looked at again on the next run, when
     * it is either no longer PENDING or still due. Safe to call repeatedly.
     *
     * @return true when this call expired the order
     */
    @Transactional
    public boolean expireIfDue(Long orderId) {
        Optional<OrderEntity> due = orderRepository.findExpiredReservationForUpdate(
                orderId, OrderStatus.PENDING, commerceClock.instant());
        due.ifPresent(this::expire);
        return due.isPresent();
    }

    /**
     * Time control for tests and the lab: ends the reservation of a PENDING order now
     * ({@code reservation_expires_at = now()}) and expires it at once. Waits for the order's row lock,
     * so a concurrent capture or cancel decides first.
     */
    @Transactional
    public OrderDto expireReservationNow(Long orderId) {
        OrderEntity order = lockOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CustomException(
                    "Only PENDING orders hold a reservation; order is " + order.getStatus(), HttpStatus.CONFLICT);
        }
        order.setReservationExpiresAt(commerceClock.instant().truncatedTo(ChronoUnit.MILLIS));
        return OrderDto.from(expire(order));
    }

    private OrderEntity expire(OrderEntity order) {
        Instant expiredAt = order.getReservationExpiresAt();
        OrderEntity saved = releaseAndCancel(order, CancellationReason.RESERVATION_EXPIRED);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", saved.getId());
        data.put("expiredAt", UtcMillis.format(expiredAt));
        data.put("totalAmount", saved.getTotalAmount());
        commerceEvents.publishData(CommerceEventType.ORDER_RESERVATION_EXPIRED, saved.getUsername(), saved.getId(), data);
        flushBeforeMapping();
        return saved;
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
        flushBeforeMapping();
        return saved;
    }

    private OrderEntity lockOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
    }

    private static FulfilmentState fulfilmentOf(OrderEntity order) {
        return FulfilmentState.orNone(order.getFulfilmentState());
    }

    private OrderDto cancelLocked(OrderEntity order, CancellationReason reason) {
        if (!canBeCancelled(order.getStatus())) {
            throw new CustomException("Order cannot be cancelled in current status", HttpStatus.BAD_REQUEST);
        }
        if (!fulfilmentOf(order).allowsCustomerCancellation()) {
            throw new CustomException(
                    "Order is already being packed and cannot be cancelled", HttpStatus.CONFLICT);
        }
        return OrderDto.from(releaseAndCancel(order, reason));
    }

    /**
     * Cancels the order. Stock is not touched here any more: when the order still holds deducted
     * stock, the {@code order.cancelled} event carries {@code stockRestore} (the lines and the actor
     * the restore movements get) and warehouse-service restores the stock from the event feed, after
     * which the projection catches up through the level push.
     */
    private OrderEntity releaseAndCancel(OrderEntity order, CancellationReason reason) {
        couponService.releaseRedemption(order);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("reason", reason.name());
        if (order.getInventoryState() == InventoryState.DEDUCTED) {
            extra.put("stockRestore", stockRestore(order, reason.actorFor(order.getUsername())));
            order.setInventoryState(InventoryState.RESTORED);
        }
        order.setStatus(OrderStatus.CANCELLED);
        // Only orders the warehouse already holds get a CANCELLED fulfilment state;
        // untouched orders stay NONE so their public body is unchanged.
        if (fulfilmentOf(order) != FulfilmentState.NONE) {
            order.setFulfilmentState(FulfilmentState.CANCELLED);
        }
        OrderEntity saved = orderRepository.save(order);
        commerceEvents.publish(CommerceEventType.ORDER_CANCELLED, saved, extra);
        flushBeforeMapping();
        return saved;
    }

    /** {@code {"actor": ..., "lines": [{"productId": 1, "quantity": 2}]}}, lines in product id order. */
    static Map<String, Object> stockRestore(OrderEntity order, String actor) {
        Map<Long, Integer> quantities = order.getItems().stream()
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getId(),
                        TreeMap::new,
                        Collectors.summingInt(OrderItemEntity::getQuantity)));
        List<Map<String, Object>> lines = new ArrayList<>(quantities.size());
        quantities.forEach((productId, quantity) -> {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("productId", productId);
            line.put("quantity", quantity);
            lines.add(line);
        });
        Map<String, Object> restore = new LinkedHashMap<>();
        restore.put("actor", actor);
        restore.put("lines", lines);
        return restore;
    }

    /**
     * Writes the order, and whatever the synchronous event listeners changed,
     * before the response is mapped, so the response carries the updatedAt that
     * {@code @UpdateTimestamp} stores and the next read returns.
     */
    private void flushBeforeMapping() {
        orderRepository.flush();
    }

}
