package com.awesome.testing.service.fulfilment;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.fulfilment.PickListDto;
import com.awesome.testing.dto.fulfilment.PickListItemDto;
import com.awesome.testing.dto.fulfilment.ShipmentDto;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.OrderItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.fulfilment.Carrier;
import com.awesome.testing.entity.fulfilment.ShipmentEntity;
import com.awesome.testing.entity.fulfilment.ShipmentStatus;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.fulfilment.ShipmentRepository;
import com.awesome.testing.service.OrderService;
import com.awesome.testing.service.notification.ShipmentStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Warehouse shipment state machine. Every transition locks the order row
 * before the shipment row, the same order used by order cancellation, so a
 * customer cancelling and a picker starting work cannot interleave.
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock commerceClock;

    @Transactional(readOnly = true)
    public Page<OrderDto> queue(Pageable pageable) {
        return orderRepository.findOrdersAwaitingShipment(pageable).map(OrderDto::from);
    }

    @Transactional
    public ShipmentDto create(Long orderId, String actor) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
        if (order.getStatus() != OrderStatus.PAID) {
            throw new CustomException("Only PAID orders can be released to the warehouse", HttpStatus.CONFLICT);
        }
        if (shipmentRepository.existsByOrderId(orderId)) {
            throw new CustomException("Order already has a shipment", HttpStatus.CONFLICT);
        }
        ShipmentEntity shipment = shipmentRepository.saveAndFlush(ShipmentEntity.builder()
                .order(order)
                .status(ShipmentStatus.AWAITING_PICK)
                .createdBy(actor)
                .lastActor(actor)
                .build());
        return toDto(shipment);
    }

    @Transactional(readOnly = true)
    public Page<ShipmentDto> list(ShipmentStatus status, Pageable pageable) {
        Page<ShipmentEntity> page = status == null
                ? shipmentRepository.findAllBy(pageable)
                : shipmentRepository.findByStatus(status, pageable);
        return page.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ShipmentDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public PickListDto pickList(Long id) {
        ShipmentEntity shipment = find(id);
        OrderEntity order = shipment.getOrder();
        Map<Long, PickListItemDto> byProduct = new LinkedHashMap<>();
        for (OrderItemEntity item : order.getItems()) {
            ProductEntity product = item.getProduct();
            byProduct.merge(product.getId(),
                    PickListItemDto.builder()
                            .productId(product.getId())
                            .productName(product.getName())
                            .category(product.getCategory())
                            .quantity(item.getQuantity())
                            .build(),
                    (left, right) -> {
                        left.setQuantity(left.getQuantity() + right.getQuantity());
                        return left;
                    });
        }
        List<PickListItemDto> items = byProduct.values().stream()
                .sorted(Comparator.comparing((PickListItemDto i) -> i.getCategory().toLowerCase(Locale.ROOT))
                        .thenComparing(i -> i.getProductName().toLowerCase(Locale.ROOT)))
                .toList();
        return PickListDto.builder()
                .shipmentId(shipment.getId())
                .orderId(order.getId())
                .status(shipment.getStatus())
                .totalUnits(items.stream().mapToInt(PickListItemDto::getQuantity).sum())
                .shippingAddress(AddressDto.from(order.getShippingAddress()))
                .items(items)
                .build();
    }

    @Transactional
    public ShipmentDto pick(Long id, String actor) {
        ShipmentEntity shipment = lock(id);
        require(shipment, "pick", ShipmentStatus.AWAITING_PICK);
        shipment.setStatus(ShipmentStatus.PICKED);
        shipment.setPickedAt(now());
        return save(shipment, actor);
    }

    @Transactional
    public ShipmentDto pack(Long id, BigDecimal weightKg, String actor) {
        ShipmentEntity shipment = lock(id);
        require(shipment, "pack", ShipmentStatus.PICKED);
        shipment.setWeightKg(weightKg);
        shipment.setStatus(ShipmentStatus.PACKED);
        shipment.setPackedAt(now());
        return save(shipment, actor);
    }

    @Transactional
    public ShipmentDto ship(Long id, Carrier carrier, String rawTrackingNumber, String actor) {
        String trackingNumber = rawTrackingNumber.trim().toUpperCase(Locale.ROOT);
        if (!carrier.acceptsTrackingNumber(trackingNumber)) {
            throw new CustomException(
                    "Tracking number for " + carrier + " must be " + carrier.trackingFormat(), HttpStatus.BAD_REQUEST);
        }
        ShipmentEntity shipment = lock(id);
        require(shipment, "ship", ShipmentStatus.PACKED);
        shipment.setCarrier(carrier);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus(ShipmentStatus.SHIPPED);
        shipment.setShippedAt(now());
        orderService.applyFulfilmentStatus(shipment.getOrder(), OrderStatus.SHIPPED);
        ShipmentDto result = save(shipment, actor);
        eventPublisher.publishEvent(new ShipmentStatusChangedEvent(shipment.getId(), ShipmentStatus.SHIPPED));
        return result;
    }

    @Transactional
    public ShipmentDto deliver(Long id, String actor) {
        ShipmentEntity shipment = lock(id);
        require(shipment, "deliver", ShipmentStatus.SHIPPED);
        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(now());
        orderService.applyFulfilmentStatus(shipment.getOrder(), OrderStatus.DELIVERED);
        ShipmentDto result = save(shipment, actor);
        eventPublisher.publishEvent(new ShipmentStatusChangedEvent(shipment.getId(), ShipmentStatus.DELIVERED));
        return result;
    }

    @Transactional
    public ShipmentDto cancel(Long id, String actor) {
        ShipmentEntity shipment = lock(id);
        require(shipment, "cancel", ShipmentStatus.AWAITING_PICK, ShipmentStatus.PICKED);
        orderService.cancelForWarehouse(shipment.getOrder());
        shipment.setLastActor(actor);
        return toDto(shipmentRepository.saveAndFlush(shipment));
    }

    @Transactional(readOnly = true)
    public ShipmentDto trackOrder(Long orderId, String username, boolean admin) {
        OrderEntity order = orderRepository.findById(orderId)
                .filter(o -> admin || o.getUsername().equals(username))
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
        return shipmentRepository.findByOrderId(order.getId())
                .map(this::toDto)
                .orElseThrow(() -> new CustomException("Shipment not found", HttpStatus.NOT_FOUND));
    }

    private ShipmentEntity lock(Long id) {
        ShipmentEntity unlocked = find(id);
        orderRepository.findByIdForUpdate(unlocked.getOrder().getId())
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
        return shipmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException("Shipment not found", HttpStatus.NOT_FOUND));
    }

    private ShipmentEntity find(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new CustomException("Shipment not found", HttpStatus.NOT_FOUND));
    }

    private static void require(ShipmentEntity shipment, String action, ShipmentStatus... allowed) {
        if (!Set.of(allowed).contains(shipment.getStatus())) {
            throw new CustomException(
                    "Cannot " + action + " a shipment in status " + shipment.getStatus(), HttpStatus.CONFLICT);
        }
    }

    private ShipmentDto save(ShipmentEntity shipment, String actor) {
        shipment.setLastActor(actor);
        return toDto(shipmentRepository.saveAndFlush(shipment));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(commerceClock);
    }

    private ShipmentDto toDto(ShipmentEntity shipment) {
        OrderEntity order = shipment.getOrder();
        return ShipmentDto.builder()
                .id(shipment.getId())
                .orderId(order.getId())
                .username(order.getUsername())
                .status(shipment.getStatus())
                .totalUnits(order.getItems().stream().mapToInt(OrderItemEntity::getQuantity).sum())
                .carrier(shipment.getCarrier())
                .trackingNumber(shipment.getTrackingNumber())
                .weightKg(shipment.getWeightKg())
                .createdBy(shipment.getCreatedBy())
                .lastActor(shipment.getLastActor())
                .createdAt(shipment.getCreatedAt())
                .updatedAt(shipment.getUpdatedAt())
                .pickedAt(shipment.getPickedAt())
                .packedAt(shipment.getPackedAt())
                .shippedAt(shipment.getShippedAt())
                .deliveredAt(shipment.getDeliveredAt())
                .cancelledAt(shipment.getCancelledAt())
                .build();
    }
}
