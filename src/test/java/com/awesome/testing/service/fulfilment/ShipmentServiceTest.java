package com.awesome.testing.service.fulfilment;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.fulfilment.Carrier;
import com.awesome.testing.entity.fulfilment.ShipmentEntity;
import com.awesome.testing.entity.fulfilment.ShipmentStatus;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.fulfilment.ShipmentRepository;
import com.awesome.testing.service.OrderService;
import com.awesome.testing.service.notification.ShipmentStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 12, 0);

    @Mock private ShipmentRepository shipmentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ShipmentService service;
    private OrderEntity order;
    private ShipmentEntity shipment;

    @BeforeEach
    void setUp() {
        service = new ShipmentService(shipmentRepository, orderRepository, orderService, eventPublisher,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        order = OrderEntity.builder().id(42L).username("buyer").status(OrderStatus.PAID).build();
        shipment = ShipmentEntity.builder().id(7L).order(order).status(ShipmentStatus.AWAITING_PICK).build();
        lenient().when(shipmentRepository.findById(7L)).thenReturn(Optional.of(shipment));
        lenient().when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        lenient().when(shipmentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(shipment));
        lenient().when(shipmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void transitionsLockTheOrderBeforeTheShipment() {
        service.pick(7L, "picker");

        InOrder locks = inOrder(orderRepository, shipmentRepository);
        locks.verify(orderRepository).findByIdForUpdate(42L);
        locks.verify(shipmentRepository).findByIdForUpdate(7L);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PICKED);
        assertThat(shipment.getPickedAt()).isEqualTo(NOW);
        assertThat(shipment.getLastActor()).isEqualTo("picker");
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"PICKED", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED"})
    void pickIsOnlyAllowedWhileAwaitingPick(ShipmentStatus status) {
        shipment.setStatus(status);

        assertThatThrownBy(() -> service.pick(7L, "picker"))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Cannot pick a shipment in status " + status);
                });
    }

    @Test
    void shippingNormalisesTheTrackingNumberUpdatesTheOrderAndPublishesAnEvent() {
        shipment.setStatus(ShipmentStatus.PACKED);

        service.ship(7L, Carrier.UPS, " 1z999aa10123456784 ", "packer");

        assertThat(shipment.getTrackingNumber()).isEqualTo("1Z999AA10123456784");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        verify(orderService).applyFulfilmentStatus(order, OrderStatus.SHIPPED);
        verify(eventPublisher).publishEvent(new ShipmentStatusChangedEvent(7L, ShipmentStatus.SHIPPED));
    }

    @Test
    void invalidTrackingNumbersAreRejectedBeforeAnyLockOrChange() {
        shipment.setStatus(ShipmentStatus.PACKED);

        assertThatThrownBy(() -> service.ship(7L, Carrier.DHL, "123", "packer"))
                .hasMessage("Tracking number for DHL must be 10 digits");
        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(orderService, never()).applyFulfilmentStatus(any(), any());
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PACKED);
    }

    @Test
    void deliveryMarksTheOrderDelivered() {
        shipment.setStatus(ShipmentStatus.SHIPPED);

        service.deliver(7L, "driver");

        assertThat(shipment.getDeliveredAt()).isEqualTo(NOW);
        verify(orderService).applyFulfilmentStatus(order, OrderStatus.DELIVERED);
        verify(eventPublisher).publishEvent(new ShipmentStatusChangedEvent(7L, ShipmentStatus.DELIVERED));
    }

    @Test
    void packingRecordsTheWeight() {
        shipment.setStatus(ShipmentStatus.PICKED);

        service.pack(7L, new BigDecimal("2.40"), "packer");

        assertThat(shipment.getWeightKg()).isEqualByComparingTo("2.40");
        assertThat(shipment.getPackedAt()).isEqualTo(NOW);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PACKED);
    }

    @Test
    void cancellationIsDelegatedToTheOrderOnlyBeforePacking() {
        shipment.setStatus(ShipmentStatus.PICKED);
        service.cancel(7L, "lead");
        verify(orderService).cancelForWarehouse(order);

        shipment.setStatus(ShipmentStatus.PACKED);
        assertThatThrownBy(() -> service.cancel(7L, "lead")).hasMessage("Cannot cancel a shipment in status PACKED");
    }

    @Test
    void onlyPaidOrdersWithoutAShipmentCanBeReleased() {
        order.setStatus(OrderStatus.PENDING);
        assertThatThrownBy(() -> service.create(42L, "lead")).hasMessage("Only PAID orders can be released to the warehouse");

        order.setStatus(OrderStatus.PAID);
        when(shipmentRepository.existsByOrderId(42L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(42L, "lead")).hasMessage("Order already has a shipment");
    }

    @Test
    void customersCanOnlyTrackTheirOwnOrders() {
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.trackOrder(42L, "stranger", false)).hasMessage("Order not found");
        when(shipmentRepository.findByOrderId(42L)).thenReturn(Optional.of(shipment));
        assertThat(service.trackOrder(42L, "stranger", true).getId()).isEqualTo(7L);
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"AWAITING_PICK", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED"})
    void packRequiresAPickedShipment(ShipmentStatus status) {
        shipment.setStatus(status);
        assertThatThrownBy(() -> service.pack(7L, BigDecimal.ONE, "packer")).hasMessage("Cannot pack a shipment in status " + status);
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"AWAITING_PICK", "PICKED", "SHIPPED", "DELIVERED", "CANCELLED"})
    void shipRequiresAPackedShipment(ShipmentStatus status) {
        shipment.setStatus(status);
        assertThatThrownBy(() -> service.ship(7L, Carrier.DHL, "1234567890", "packer")).hasMessage("Cannot ship a shipment in status " + status);
        verify(orderService, never()).applyFulfilmentStatus(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"AWAITING_PICK", "PICKED", "PACKED", "DELIVERED", "CANCELLED"})
    void deliverRequiresAShippedShipment(ShipmentStatus status) {
        shipment.setStatus(status);
        assertThatThrownBy(() -> service.deliver(7L, "driver")).hasMessage("Cannot deliver a shipment in status " + status);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void transitionsReturnTheUpdatedShipmentView() {
        order.setItems(new java.util.ArrayList<>(java.util.List.of(
                com.awesome.testing.entity.OrderItemEntity.builder().quantity(2).build(),
                com.awesome.testing.entity.OrderItemEntity.builder().quantity(3).build())));

        assertThat(service.pick(7L, "picker")).satisfies(dto -> {
            assertThat(dto.getStatus()).isEqualTo(ShipmentStatus.PICKED);
            assertThat(dto.getTotalUnits()).isEqualTo(5);
            assertThat(dto.getLastActor()).isEqualTo("picker");
        });
        assertThat(service.pack(7L, BigDecimal.TEN, "packer").getStatus()).isEqualTo(ShipmentStatus.PACKED);
        assertThat(service.ship(7L, Carrier.DPD, "12345678901234", "packer")).satisfies(dto -> {
            assertThat(dto.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
            assertThat(dto.getCarrier()).isEqualTo(Carrier.DPD);
            assertThat(dto.getShippedAt()).isEqualTo(NOW);
        });
        assertThat(service.deliver(7L, "driver").getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void warehouseCancellationRecordsWhoCancelled() {
        org.mockito.Mockito.doAnswer(invocation -> {
            shipment.setStatus(ShipmentStatus.CANCELLED);
            return null;
        }).when(orderService).cancelForWarehouse(order);

        assertThat(service.cancel(7L, "lead")).satisfies(dto -> {
            assertThat(dto.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
            assertThat(dto.getLastActor()).isEqualTo("lead");
        });
    }
}
