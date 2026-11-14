package com.awesome.testing.service;

import com.awesome.testing.service.reservation.OrderReservations;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.OrderIdempotencyKeyEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.coupon.CouponEntity;
import com.awesome.testing.entity.inventory.InventoryState;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderIdempotencyKeyRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.service.coupon.CouponQuote;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.service.checkout.CheckoutContributors;
import com.awesome.testing.service.coupon.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Coupon and idempotency rules added to checkout and cancellation. */
@ExtendWith(MockitoExtension.class)
class OrderServiceCommerceTest {

    private static final String USER = "buyer";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 12, 0);

    @Mock private OrderRepository orderRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryService inventoryService;
    @Mock private CouponService couponService;
    @Mock private OrderIdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private CommerceEvents commerceEvents;

    private OrderService service;
    private ProductEntity product;
    private AddressDto address;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, cartItemRepository, productRepository, inventoryService, couponService,
                idempotencyKeyRepository, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                CheckoutContributors.none(), commerceEvents,
                new OrderReservations(true, Duration.ofMinutes(30)));
        product = ProductEntity.builder().id(1L).name("Phone").price(new BigDecimal("40.00")).stockQuantity(10).build();
        address = AddressDto.builder().street("Main 1").city("Warsaw").state("MZ").zipCode("00-001").country("PL").build();
        lenient().when(cartItemRepository.findByUsernameForUpdate(USER)).thenReturn(List.of(CartItemEntity.builder()
                .username(USER).product(product).quantity(3).price(product.getPrice()).build()));
        lenient().when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        lenient().when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(99L);
            }
            return order;
        });
    }

    @Test
    void checkoutAppliesTheQuotedCouponAndRecordsTheRedemption() {
        CouponEntity coupon = CouponEntity.builder().id(5L).code("SAVE20").build();
        CouponQuote quote = new CouponQuote(coupon, new BigDecimal("20.00"));
        when(couponService.quoteForCheckout(USER, new BigDecimal("120.00"))).thenReturn(Optional.of(quote));

        var created = service.createOrder(USER, address, null);

        assertThat(created.replayed()).isFalse();
        assertThat(created.order().getSubtotalAmount()).isEqualByComparingTo("120.00");
        assertThat(created.order().getDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(created.order().getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(created.order().getCouponCode()).isEqualTo("SAVE20");
        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        verify(couponService).recordRedemption(eq(quote), saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(99L);
        verify(idempotencyKeyRepository, never()).save(any());
    }

    @Test
    void checkoutWithoutACouponChargesTheSubtotal() {
        when(couponService.quoteForCheckout(any(), any())).thenReturn(Optional.empty());

        var created = service.createOrder(USER, address, null);

        assertThat(created.order().getDiscountAmount()).as("absent without a coupon (course contract)").isNull();
        assertThat(created.order().getSubtotalAmount()).isNull();
        assertThat(created.order().getTotalAmount()).isEqualByComparingTo("120.00");
        assertThat(created.order().getCouponCode()).isNull();
        verify(couponService, never()).recordRedemption(any(), any());
    }

    @Test
    void anIdempotencyKeyIsStoredWithTheNewOrder() {
        when(couponService.quoteForCheckout(any(), any())).thenReturn(Optional.empty());

        service.createOrder(USER, address, "checkout-key-1");

        ArgumentCaptor<OrderIdempotencyKeyEntity> key = ArgumentCaptor.forClass(OrderIdempotencyKeyEntity.class);
        verify(idempotencyKeyRepository).save(key.capture());
        assertThat(key.getValue().getKey()).isEqualTo("checkout-key-1");
        assertThat(key.getValue().getRequestHash()).isEqualTo(OrderService.requestHash(address)).hasSize(64);
        verify(idempotencyKeyRepository).deleteExpired(USER, "checkout-key-1", NOW.minusHours(24));
    }

    @Test
    void aRepeatedKeyReplaysWithoutTouchingStockAndADifferentAddressConflicts() {
        OrderEntity original = OrderEntity.builder().id(7L).username(USER).shippingAddress(AddressEntity.from(address)).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).build();
        when(idempotencyKeyRepository.findByUsernameAndKey(USER, "checkout-key-1")).thenReturn(Optional.of(
                OrderIdempotencyKeyEntity.builder().order(original).requestHash(OrderService.requestHash(address)).build()));

        var replay = service.createOrder(USER, address, "checkout-key-1");
        AddressDto other = AddressDto.builder().street("Other 2").city("Warsaw").state("MZ").zipCode("00-001").country("PL").build();

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.order().getId()).isEqualTo(7L);
        verify(inventoryService, never()).deduct(any(), any(Integer.class), any());
        assertThatThrownBy(() -> service.createOrder(USER, other, "checkout-key-1"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void malformedKeysAreRejectedBeforeTheCartIsLocked() {
        assertThatThrownBy(() -> service.createOrder(USER, address, "short"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createOrder(USER, address, "x".repeat(65))).isInstanceOf(CustomException.class);
        verify(cartItemRepository, never()).findByUsernameForUpdate(any());
        assertThat(service.createOrder(USER, address, "a".repeat(8)).replayed()).isFalse();
    }

    @Test
    void cancellingReleasesTheCouponRedemption() {
        OrderEntity order = OrderEntity.builder().id(7L).username(USER).shippingAddress(AddressEntity.from(address)).status(OrderStatus.PAID)
                .inventoryState(InventoryState.LEGACY_UNTRACKED).items(new ArrayList<>()).build();
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        service.cancelOrder(7L, USER, false);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(couponService).releaseRedemption(order);
    }

    @Test
    void cancellingAShippedOrderIsRejectedWithoutReleasingTheCoupon() {
        OrderEntity order = OrderEntity.builder().id(7L).username(USER).shippingAddress(AddressEntity.from(address)).status(OrderStatus.SHIPPED)
                .items(new ArrayList<>()).build();
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(7L, USER, false))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(couponService, never()).releaseRedemption(any());
    }
}
