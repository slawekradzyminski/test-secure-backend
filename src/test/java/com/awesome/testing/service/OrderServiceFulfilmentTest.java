package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.OrderItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryState;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderIdempotencyKeyRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cancellation, manual status and fulfilment transition rules driven by the order's fulfilment state. */
@ExtendWith(MockitoExtension.class)
class OrderServiceFulfilmentTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryService inventoryService;
    @Mock private CouponService couponService;
    @Mock private OrderIdempotencyKeyRepository idempotencyKeyRepository;

    private OrderService service;
    private OrderEntity order;
    private ProductEntity product;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, cartItemRepository, productRepository, inventoryService, couponService,
                idempotencyKeyRepository, Clock.systemUTC());
        product = ProductEntity.builder().id(4L).name("Phone").price(BigDecimal.TEN).stockQuantity(0).build();
        order = OrderEntity.builder().id(7L).username("buyer").status(OrderStatus.PAID)
                .shippingAddress(new AddressEntity()).totalAmount(BigDecimal.TEN)
                .inventoryState(InventoryState.DEDUCTED).items(new ArrayList<>()).build();
        order.addItem(OrderItemEntity.builder().product(product).quantity(2).price(BigDecimal.TEN).build());
        lenient().when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(product));
    }

    @ParameterizedTest
    @EnumSource(value = FulfilmentState.class, names = {"NONE", "AWAITING_PICK"})
    void customersMayCancelBeforePickingAndTheStateBecomesCancelled(FulfilmentState state) {
        order.setFulfilmentState(state);

        var cancelled = service.cancelOrder(7L, "buyer", false);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFulfilmentState())
                .as("only orders the warehouse holds become CANCELLED; untouched ones stay NONE")
                .isEqualTo(state == FulfilmentState.NONE ? FulfilmentState.NONE : FulfilmentState.CANCELLED);
        assertThat(order.getInventoryState()).isEqualTo(InventoryState.RESTORED);
        verify(inventoryService).restore(product, 2, order);
        verify(couponService).releaseRedemption(order);
    }

    @ParameterizedTest
    @EnumSource(value = FulfilmentState.class, names = {"PICKED", "PACKED"})
    void customersAndAdminsCannotCancelOncePickingStarted(FulfilmentState state) {
        order.setFulfilmentState(state);

        assertConflict(() -> service.cancelOrder(7L, "buyer", false));
        assertConflict(() -> service.cancelOrder(7L, "admin", true));
        assertConflict(() -> service.updateOrderStatus(7L, OrderStatus.CANCELLED));
        verify(couponService, never()).releaseRedemption(any());
        verify(inventoryService, never()).restore(any(), anyInt(), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void legacyRowsWithoutAStateBehaveAsNone() {
        order.setFulfilmentState(null);

        assertThat(service.updateOrderStatus(7L, OrderStatus.SHIPPED).getFulfilmentState())
                .as("NONE is omitted from the public order body").isNull();
        order.setStatus(OrderStatus.PAID);
        assertThat(service.cancelOrder(7L, "buyer", false).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = FulfilmentState.class, names = {"AWAITING_PICK", "PICKED", "PACKED", "SHIPPED"})
    void manualStatusChangesAreBlockedWhileTheWarehouseOwnsTheOrder(FulfilmentState state) {
        order.setFulfilmentState(state);

        assertConflict(() -> service.updateOrderStatus(7L, OrderStatus.DELIVERED));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @ParameterizedTest
    @EnumSource(value = FulfilmentState.class, names = {"NONE", "DELIVERED", "CANCELLED"})
    void manualStatusChangesAreAllowedOutsideTheWarehouseFlow(FulfilmentState state) {
        order.setFulfilmentState(state);

        assertThat(service.updateOrderStatus(7L, OrderStatus.SHIPPED).getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void releasingRequiresAPaidOrderWithoutAState() {
        order.setStatus(OrderStatus.PENDING);
        assertConflict(() -> service.applyFulfilmentState(7L, FulfilmentState.AWAITING_PICK));

        order.setStatus(OrderStatus.PAID);
        assertThat(service.applyFulfilmentState(7L, FulfilmentState.AWAITING_PICK).getFulfilmentState())
                .isEqualTo(FulfilmentState.AWAITING_PICK);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertConflict(() -> service.applyFulfilmentState(7L, FulfilmentState.AWAITING_PICK));
    }

    @Test
    void transitionsMoveOneStepForwardAndSetShippedAndDelivered() {
        order.setFulfilmentState(FulfilmentState.AWAITING_PICK);

        assertConflict(() -> service.applyFulfilmentState(7L, FulfilmentState.PACKED));
        service.applyFulfilmentState(7L, FulfilmentState.PICKED);
        service.applyFulfilmentState(7L, FulfilmentState.PACKED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        service.applyFulfilmentState(7L, FulfilmentState.SHIPPED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertConflict(() -> service.applyFulfilmentState(7L, FulfilmentState.PACKED));
        service.applyFulfilmentState(7L, FulfilmentState.DELIVERED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getFulfilmentState()).isEqualTo(FulfilmentState.DELIVERED);
    }

    @Test
    void repeatingTheCurrentStepIsANoOp() {
        order.setFulfilmentState(FulfilmentState.SHIPPED);
        order.setStatus(OrderStatus.SHIPPED);

        assertThat(service.applyFulfilmentState(7L, FulfilmentState.SHIPPED)).isSameAs(order);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void transitionsFromCancelledOrToNoneOrCancelledAreRejected() {
        order.setFulfilmentState(FulfilmentState.CANCELLED);
        assertConflict(() -> service.applyFulfilmentState(7L, FulfilmentState.PICKED));

        assertThatThrownBy(() -> service.applyFulfilmentState(7L, FulfilmentState.CANCELLED))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.applyFulfilmentState(7L, FulfilmentState.NONE)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.applyFulfilmentState(7L, null)).isInstanceOf(CustomException.class);
    }

    @Test
    void unknownOrdersAreNotFound() {
        when(orderRepository.findByIdForUpdate(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyFulfilmentState(8L, FulfilmentState.PICKED))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.cancelForWarehouse(8L))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @ParameterizedTest
    @EnumSource(value = FulfilmentState.class, names = {"AWAITING_PICK", "PICKED"})
    void warehouseCancelRestoresStockAndReleasesTheCoupon(FulfilmentState state) {
        order.setFulfilmentState(state);

        OrderEntity cancelled = service.cancelForWarehouse(7L);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFulfilmentState())
                .as("only orders the warehouse holds become CANCELLED; untouched ones stay NONE")
                .isEqualTo(state == FulfilmentState.NONE ? FulfilmentState.NONE : FulfilmentState.CANCELLED);
        verify(inventoryService).restore(product, 2, order);
        verify(couponService).releaseRedemption(order);
    }

    @ParameterizedTest
    @EnumSource(value = FulfilmentState.class, names = {"NONE", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED"})
    void warehouseCancelIsRejectedOutsideAwaitingPickAndPicked(FulfilmentState state) {
        order.setFulfilmentState(state);

        assertConflict(() -> service.cancelForWarehouse(7L));
        verify(inventoryService, never()).restore(any(), anyInt(), any());
    }

    @Test
    void warehouseCancelRequiresAPaidOrder() {
        order.setFulfilmentState(FulfilmentState.PICKED);
        order.setStatus(OrderStatus.PENDING);

        assertConflict(() -> service.cancelForWarehouse(7L));
        verify(couponService, never()).releaseRedemption(any());
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
}
