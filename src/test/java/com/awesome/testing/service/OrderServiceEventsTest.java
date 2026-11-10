package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.OrderIdempotencyKeyEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryState;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderIdempotencyKeyRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.service.checkout.CheckoutContext;
import com.awesome.testing.service.checkout.CheckoutContributor;
import com.awesome.testing.service.checkout.CheckoutContributors;
import com.awesome.testing.service.coupon.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Domain events and checkout contributors around order state changes. */
@ExtendWith(MockitoExtension.class)
class OrderServiceEventsTest {

    private static final String USER = "buyer";

    @Mock private OrderRepository orderRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryService inventoryService;
    @Mock private CouponService couponService;
    @Mock private OrderIdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private CommerceEvents commerceEvents;

    private final List<CheckoutContext> contributions = new ArrayList<>();
    private final List<OrderStatus> statusesSeenByContributors = new ArrayList<>();
    private OrderService service;
    private OrderEntity order;
    private ProductEntity product;
    private AddressDto address;

    @BeforeEach
    void setUp() {
        CheckoutContributor recording = context -> {
            contributions.add(context);
            statusesSeenByContributors.add(context.order().getStatus());
        };
        service = service(new CheckoutContributors(List.of(recording)));
        product = ProductEntity.builder().id(4L).name("Phone").price(new BigDecimal("40.00")).stockQuantity(10).build();
        address = AddressDto.builder().street("Main 1").city("Warsaw").state("MZ").zipCode("00-001").country("PL").build();
        order = OrderEntity.builder().id(7L).username(USER).status(OrderStatus.PENDING)
                .shippingAddress(new AddressEntity()).totalAmount(BigDecimal.TEN)
                .inventoryState(InventoryState.DEDUCTED).items(new ArrayList<>()).build();
        lenient().when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        lenient().when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(product));
        lenient().when(cartItemRepository.findByUsernameForUpdate(USER)).thenReturn(List.of(CartItemEntity.builder()
                .username(USER).product(product).quantity(3).price(product.getPrice()).build()));
        lenient().when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(99L);
            }
            return saved;
        });
    }

    private OrderService service(CheckoutContributors contributors) {
        return new OrderService(orderRepository, cartItemRepository, productRepository, inventoryService, couponService,
                idempotencyKeyRepository, Clock.systemUTC(), contributors, commerceEvents);
    }

    @Test
    void checkoutRunsContributorsOnThePricedUnsavedOrderAndPublishesOrderCreated() {
        when(couponService.quoteForCheckout(any(), any())).thenReturn(Optional.empty());

        var created = service.createOrder(USER, address, null);

        assertThat(contributions).singleElement().satisfies(context -> {
            assertThat(context.username()).isEqualTo(USER);
            assertThat(context.address()).isSameAs(address);
            assertThat(context.cartItems()).singleElement()
                    .satisfies(item -> assertThat(item.getProduct()).isSameAs(product));
            assertThat(context.order().getTotalAmount()).isEqualByComparingTo("120.00");
            assertThat(context.order().getSubtotalAmount()).isEqualByComparingTo("120.00");
            assertThat(context.order().getItems()).hasSize(1);
        });
        assertThat(statusesSeenByContributors).containsExactly(OrderStatus.PENDING);
        assertThat(created.order().getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(commerceEvents).publish(CommerceEventType.ORDER_CREATED, contributions.getFirst().order());
        assertThat(contributions.getFirst().order().getId()).isEqualTo(99L);
    }

    @Test
    void aContributorCanRejectCheckoutBeforeAnythingIsSaved() {
        when(couponService.quoteForCheckout(any(), any())).thenReturn(Optional.empty());
        OrderService rejecting = service(new CheckoutContributors(List.of(context -> {
            throw new CustomException("Gift card is empty", HttpStatus.CONFLICT);
        })));

        assertThatThrownBy(() -> rejecting.createOrder(USER, address, null)).hasMessage("Gift card is empty");
        verify(orderRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteByUsername(any());
        verifyNoInteractions(commerceEvents);
    }

    @Test
    void replaysAndEmptyCartsPublishNothing() {
        OrderEntity original = OrderEntity.builder().id(5L).username(USER).status(OrderStatus.PENDING)
                .shippingAddress(AddressEntity.from(address)).totalAmount(BigDecimal.TEN).build();
        when(idempotencyKeyRepository.findByUsernameAndKey(USER, "checkout-key-1")).thenReturn(Optional.of(
                OrderIdempotencyKeyEntity.builder().order(original).requestHash(OrderService.requestHash(address)).build()));

        assertThat(service.createOrder(USER, address, "checkout-key-1").replayed()).isTrue();
        when(cartItemRepository.findByUsernameForUpdate("empty")).thenReturn(List.of());
        assertThatThrownBy(() -> service.createOrder("empty", address, null)).isInstanceOf(CustomException.class);

        assertThat(contributions).isEmpty();
        verifyNoInteractions(commerceEvents);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "SHIPPED", "DELIVERED"})
    void adminStatusChangesPublishTheReachedStatus(OrderStatus target) {
        service.updateOrderStatus(7L, target);

        verify(commerceEvents).publish(CommerceEventType.reaching(target).orElseThrow(), order);
    }

    @Test
    void settingTheCurrentStatusOrPendingPublishesNothing() {
        service.updateOrderStatus(7L, OrderStatus.PENDING);
        order.setStatus(OrderStatus.PAID);
        service.updateOrderStatus(7L, OrderStatus.PAID);

        verifyNoInteractions(commerceEvents);
    }

    @Test
    void everyCancellationPathPublishesOrderCancelledOnce() {
        service.cancelOrder(7L, USER, false);
        verify(commerceEvents).publish(CommerceEventType.ORDER_CANCELLED, order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> service.updateOrderStatus(7L, OrderStatus.CANCELLED)).isInstanceOf(CustomException.class);

        order.setStatus(OrderStatus.PAID);
        order.setFulfilmentState(FulfilmentState.PICKED);
        service.cancelForWarehouse(7L);

        verify(commerceEvents, times(2)).publish(CommerceEventType.ORDER_CANCELLED, order);
    }

    @Test
    void aRejectedCancellationPublishesNothing() {
        assertThatThrownBy(() -> service.cancelOrder(7L, "someone-else", false)).isInstanceOf(CustomException.class);
        order.setFulfilmentState(FulfilmentState.PICKED);
        assertThatThrownBy(() -> service.cancelOrder(7L, USER, false)).isInstanceOf(CustomException.class);

        verifyNoInteractions(commerceEvents);
    }

    @Test
    void fulfilmentPublishesOnlyWhenTheOrderShipsOrIsDelivered() {
        order.setStatus(OrderStatus.PAID);
        service.applyFulfilmentState(7L, FulfilmentState.AWAITING_PICK);
        service.applyFulfilmentState(7L, FulfilmentState.PICKED);
        service.applyFulfilmentState(7L, FulfilmentState.PACKED);
        verifyNoInteractions(commerceEvents);

        service.applyFulfilmentState(7L, FulfilmentState.SHIPPED);
        service.applyFulfilmentState(7L, FulfilmentState.SHIPPED);
        verify(commerceEvents).publish(CommerceEventType.ORDER_SHIPPED, order);

        service.applyFulfilmentState(7L, FulfilmentState.DELIVERED);
        service.applyFulfilmentState(7L, FulfilmentState.DELIVERED);
        verify(commerceEvents).publish(CommerceEventType.ORDER_DELIVERED, order);
    }
}
