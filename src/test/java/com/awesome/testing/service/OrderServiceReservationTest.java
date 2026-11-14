package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.OrderItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryState;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderIdempotencyKeyRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.service.checkout.CheckoutContributors;
import com.awesome.testing.service.coupon.CouponService;
import com.awesome.testing.service.reservation.OrderReservations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Reservation end at checkout, expiry and the cancellation reasons. */
@ExtendWith(MockitoExtension.class)
class OrderServiceReservationTest {

    private static final String USER = "buyer";
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00.123456Z");
    private static final Instant NOW_MILLIS = Instant.parse("2026-11-14T10:00:00.123Z");

    @Mock private OrderRepository orderRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryService inventoryService;
    @Mock private CouponService couponService;
    @Mock private OrderIdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private CommerceEvents commerceEvents;

    private OrderEntity order;
    private ProductEntity product;
    private AddressDto address;

    @BeforeEach
    void setUp() {
        product = ProductEntity.builder().id(4L).name("Phone").price(new BigDecimal("40.00")).stockQuantity(10).build();
        address = AddressDto.builder().street("Main 1").city("Warsaw").state("MZ").zipCode("00-001").country("PL").build();
        order = OrderEntity.builder().id(7L).username(USER).status(OrderStatus.PENDING)
                .shippingAddress(new AddressEntity()).totalAmount(new BigDecimal("80.00"))
                .inventoryState(InventoryState.DEDUCTED)
                .reservationExpiresAt(Instant.parse("2026-11-14T09:59:00.000Z"))
                .items(new ArrayList<>(List.of(OrderItemEntity.builder().product(product).quantity(2)
                        .price(product.getPrice()).build())))
                .build();
        lenient().when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(product));
        lenient().when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(99L);
            }
            return saved;
        });
    }

    private OrderService service(boolean reservations) {
        return new OrderService(orderRepository, cartItemRepository, productRepository, inventoryService, couponService,
                idempotencyKeyRepository, Clock.fixed(NOW, ZoneOffset.UTC), CheckoutContributors.none(), commerceEvents,
                new OrderReservations(reservations, Duration.ofMinutes(30)));
    }

    @Test
    void checkoutStoresTheReservationEndTtlAfterNow() {
        givenCart();

        OrderDto created = service(true).createOrder(USER, address);

        assertThat(created.getReservationExpiresAt()).isEqualTo("2026-11-14T10:30:00.123Z");
        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);
        OrderEntity saved = findSaved();
        assertThat(saved.getReservationExpiresAt()).isEqualTo(Instant.parse("2026-11-14T10:30:00.123Z"));
        verify(inventoryService).deduct(product, 2, saved);
    }

    @Test
    void checkoutWithReservationsDisabledStoresNoEnd() {
        givenCart();

        OrderDto created = service(false).createOrder(USER, address);

        assertThat(created.getReservationExpiresAt()).isNull();
        assertThat(findSaved().getReservationExpiresAt()).isNull();
    }

    @Test
    void anExpiredPendingOrderIsCancelledLikeACustomerCancelWithTheSystemActor() {
        when(orderRepository.findExpiredReservationForUpdate(7L, OrderStatus.PENDING, NOW)).thenReturn(Optional.of(order));

        boolean expired = service(true).expireIfDue(7L);

        assertThat(expired).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getInventoryState()).isEqualTo(InventoryState.RESTORED);
        assertThat(order.getFulfilmentState()).isEqualTo(FulfilmentState.NONE);
        assertThat(order.getReservationExpiresAt()).isEqualTo(Instant.parse("2026-11-14T09:59:00.000Z"));
        verify(couponService).releaseRedemption(order);
        verify(inventoryService).restore(product, 2, order, "system");
        InOrder events = inOrder(commerceEvents, orderRepository);
        events.verify(commerceEvents).publish(CommerceEventType.ORDER_CANCELLED, order,
                Map.of("reason", "RESERVATION_EXPIRED"));
        events.verify(commerceEvents).publishData(CommerceEventType.ORDER_RESERVATION_EXPIRED, USER, 7L,
                expiredData("2026-11-14T09:59:00.000Z"));
        events.verify(orderRepository).flush();
    }

    @Test
    void anOrderThatIsNotDueOrIsLockedElsewhereIsSkipped() {
        when(orderRepository.findExpiredReservationForUpdate(7L, OrderStatus.PENDING, NOW)).thenReturn(Optional.empty());

        assertThat(service(true).expireIfDue(7L)).isFalse();

        verifyNoInteractions(commerceEvents, inventoryService, couponService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void theTimeControlEndsTheReservationNowAndExpiresTheOrder() {
        order.setReservationExpiresAt(null);
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        OrderDto expired = service(true).expireReservationNow(7L);

        assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(expired.getReservationExpiresAt()).isEqualTo("2026-11-14T10:00:00.123Z");
        assertThat(order.getReservationExpiresAt()).isEqualTo(NOW_MILLIS);
        verify(inventoryService).restore(product, 2, order, "system");
        verify(commerceEvents).publish(CommerceEventType.ORDER_CANCELLED, order, Map.of("reason", "RESERVATION_EXPIRED"));
        verify(commerceEvents).publishData(CommerceEventType.ORDER_RESERVATION_EXPIRED, USER, 7L,
                expiredData("2026-11-14T10:00:00.123Z"));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void theTimeControlRejectsOrdersThatAreNotPending(OrderStatus status) {
        order.setStatus(status);
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service(true).expireReservationNow(7L))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Only PENDING orders hold a reservation; order is " + status);
                });
        assertThat(order.getStatus()).isEqualTo(status);
        assertThat(order.getReservationExpiresAt()).isEqualTo(Instant.parse("2026-11-14T09:59:00.000Z"));
        verifyNoInteractions(commerceEvents, inventoryService);
    }

    @Test
    void theTimeControlAnswers404ForAnUnknownOrder() {
        when(orderRepository.findByIdForUpdate(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(true).expireReservationNow(8L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void customerAdminAndStatusCancellationsCarryTheirReasonAndTheCustomerActor() {
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        OrderService service = service(true);

        service.cancelOrder(7L, USER, false);
        verify(commerceEvents).publish(CommerceEventType.ORDER_CANCELLED, order, Map.of("reason", "CUSTOMER"));
        verify(inventoryService).restore(product, 2, order, USER);

        reopen();
        service.cancelOrder(7L, "admin", true);
        verify(commerceEvents).publish(CommerceEventType.ORDER_CANCELLED, order, Map.of("reason", "ADMIN"));

        reopen();
        service.updateOrderStatus(7L, OrderStatus.CANCELLED);
        verify(commerceEvents, org.mockito.Mockito.times(2))
                .publish(CommerceEventType.ORDER_CANCELLED, order, Map.of("reason", "ADMIN"));
        verify(commerceEvents, never()).publishData(any(), anyString(), any(), any());
    }

    @Test
    void erasureCancellationsCarryTheAccountErasureReason() {
        when(orderRepository.findByUsernameForUpdate(USER)).thenReturn(List.of(order));

        service(true).cancelOpenOrdersForErasure(USER);

        verify(commerceEvents).publish(CommerceEventType.ORDER_CANCELLED, order, Map.of("reason", "ACCOUNT_ERASURE"));
        verify(inventoryService).restore(product, 2, order, USER);
    }

    @Test
    void ordersWithoutDeductedStockAreExpiredWithoutRestoringStock() {
        order.setInventoryState(InventoryState.LEGACY_UNTRACKED);
        when(orderRepository.findExpiredReservationForUpdate(7L, OrderStatus.PENDING, NOW)).thenReturn(Optional.of(order));

        assertThat(service(true).expireIfDue(7L)).isTrue();

        verify(inventoryService, never()).restore(any(), anyInt(), any(), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private void reopen() {
        order.setStatus(OrderStatus.PENDING);
        order.setInventoryState(InventoryState.DEDUCTED);
    }

    private static Map<String, Object> expiredData(String expiredAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", 7L);
        data.put("expiredAt", expiredAt);
        data.put("totalAmount", new BigDecimal("80.00"));
        return data;
    }

    private void givenCart() {
        when(cartItemRepository.findByUsernameForUpdate(USER)).thenReturn(List.of(CartItemEntity.builder()
                .username(USER).product(product).quantity(2).price(product.getPrice()).build()));
        when(couponService.quoteForCheckout(any(), any())).thenReturn(Optional.empty());
    }

    private OrderEntity findSaved() {
        var captor = org.mockito.ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
