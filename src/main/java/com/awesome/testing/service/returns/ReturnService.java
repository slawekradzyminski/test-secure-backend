package com.awesome.testing.service.returns;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.returns.CreateReturnDto;
import com.awesome.testing.dto.returns.ReceiveItemDto;
import com.awesome.testing.dto.returns.ReturnDto;
import com.awesome.testing.dto.returns.ReturnItemDto;
import com.awesome.testing.dto.returns.ReturnItemRequestDto;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.OrderItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.fulfilment.ShipmentEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.awesome.testing.entity.returns.ItemCondition;
import com.awesome.testing.entity.returns.ReturnItemEntity;
import com.awesome.testing.entity.returns.ReturnRequestEntity;
import com.awesome.testing.entity.returns.ReturnStatus;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.fulfilment.ShipmentRepository;
import com.awesome.testing.repository.returns.ReturnRequestRepository;
import com.awesome.testing.service.InventoryService;
import com.awesome.testing.service.coupon.CouponPricing;
import com.awesome.testing.service.notification.ReturnStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReturnService {

    static final Duration RETURN_WINDOW = Duration.ofDays(30);

    private final ReturnRequestRepository returnRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock commerceClock;

    /**
     * Opens a return. The order row is locked so concurrent requests cannot
     * together return more units than were bought.
     */
    @Transactional
    public ReturnDto create(String username, Long orderId, CreateReturnDto request) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.getUsername().equals(username))
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new CustomException("Only delivered orders can be returned", HttpStatus.CONFLICT);
        }
        LocalDateTime deliveredAt = shipmentRepository.findByOrderId(orderId)
                .map(ShipmentEntity::getDeliveredAt)
                .orElse(order.getUpdatedAt());
        if (LocalDateTime.now(commerceClock).isAfter(deliveredAt.plus(RETURN_WINDOW))) {
            throw new CustomException("The 30-day return window has passed", HttpStatus.CONFLICT);
        }

        Map<Long, Integer> ordered = new HashMap<>();
        Map<Long, OrderItemEntity> firstItem = new HashMap<>();
        for (OrderItemEntity item : order.getItems()) {
            ordered.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
            firstItem.putIfAbsent(item.getProduct().getId(), item);
        }
        Map<Long, Integer> alreadyReturned = new HashMap<>();
        for (ReturnRequestEntity previous : returnRepository.findByOrderIdAndStatusNot(orderId, ReturnStatus.REJECTED)) {
            for (ReturnItemEntity item : previous.getItems()) {
                alreadyReturned.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
            }
        }

        ReturnRequestEntity returnRequest = ReturnRequestEntity.builder()
                .order(order)
                .username(username)
                .status(ReturnStatus.REQUESTED)
                .reason(request.getReason())
                .comment(blankToNull(request.getComment()))
                .build();
        Set<Long> seen = new HashSet<>();
        for (ReturnItemRequestDto item : request.getItems()) {
            Long productId = item.getProductId();
            if (!seen.add(productId)) {
                throw new CustomException("Product " + productId + " is listed more than once", HttpStatus.BAD_REQUEST);
            }
            if (!ordered.containsKey(productId)) {
                throw new CustomException("Product " + productId + " is not part of this order", HttpStatus.BAD_REQUEST);
            }
            int returnable = ordered.get(productId) - alreadyReturned.getOrDefault(productId, 0);
            if (item.getQuantity() > returnable) {
                throw new CustomException("Requested quantity for product " + productId
                        + " exceeds the returnable quantity of " + returnable, HttpStatus.CONFLICT);
            }
            OrderItemEntity orderItem = firstItem.get(productId);
            returnRequest.addItem(ReturnItemEntity.builder()
                    .product(orderItem.getProduct())
                    .quantity(item.getQuantity())
                    .unitPrice(orderItem.getPrice())
                    .build());
        }
        return toDto(returnRepository.saveAndFlush(returnRequest));
    }

    @Transactional(readOnly = true)
    public Page<ReturnDto> mine(String username, Pageable pageable) {
        return returnRepository.findByUsername(username, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ReturnDto getMine(String username, Long id) {
        return returnRepository.findByIdAndUsername(id, username)
                .map(this::toDto)
                .orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public Page<ReturnDto> list(ReturnStatus status, Pageable pageable) {
        Page<ReturnRequestEntity> page = status == null
                ? returnRepository.findAll(pageable)
                : returnRepository.findByStatus(status, pageable);
        return page.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ReturnDto get(Long id) {
        return returnRepository.findById(id).map(this::toDto).orElseThrow(this::notFound);
    }

    @Transactional
    public ReturnDto approve(Long id) {
        ReturnRequestEntity request = lock(id);
        require(request, "approve", ReturnStatus.REQUESTED);
        request.setStatus(ReturnStatus.APPROVED);
        request.setApprovedAt(now());
        return saveAndNotify(request);
    }

    @Transactional
    public ReturnDto reject(Long id, String note) {
        ReturnRequestEntity request = lock(id);
        require(request, "reject", ReturnStatus.REQUESTED);
        request.setStatus(ReturnStatus.REJECTED);
        request.setResolutionNote(note.trim());
        request.setRejectedAt(now());
        return saveAndNotify(request);
    }

    /**
     * Records the inspection result. Resellable items go back to stock; the
     * request must name every returned product exactly once.
     */
    @Transactional
    public ReturnDto receive(Long id, List<ReceiveItemDto> conditions, String actor) {
        ReturnRequestEntity request = lock(id);
        require(request, "receive", ReturnStatus.APPROVED);
        Map<Long, ItemCondition> byProduct = new HashMap<>();
        for (ReceiveItemDto condition : conditions) {
            if (byProduct.put(condition.getProductId(), condition.getCondition()) != null) {
                throw new CustomException(
                        "Product " + condition.getProductId() + " is listed more than once", HttpStatus.BAD_REQUEST);
            }
        }
        Map<Long, ReturnItemEntity> items = request.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity(), (a, b) -> a, TreeMap::new));
        if (!byProduct.keySet().equals(items.keySet())) {
            throw new CustomException(
                    "Conditions must cover exactly the returned products " + items.keySet(), HttpStatus.BAD_REQUEST);
        }
        for (Map.Entry<Long, ReturnItemEntity> entry : items.entrySet()) {
            ReturnItemEntity item = entry.getValue();
            item.setCondition(byProduct.get(entry.getKey()));
            if (item.getCondition() == ItemCondition.RESELLABLE) {
                ProductEntity product = productRepository.findByIdForUpdate(entry.getKey())
                        .orElseThrow(() -> new CustomException("Product not found", HttpStatus.NOT_FOUND));
                inventoryService.receive(product, item.getQuantity(), InventoryMovementType.RETURN_RESTOCKED,
                        request.getOrder(), actor, "Return #" + request.getId());
            }
        }
        request.setStatus(ReturnStatus.RECEIVED);
        request.setReceivedAt(now());
        return toDto(returnRepository.saveAndFlush(request));
    }

    @Transactional
    public ReturnDto refund(Long id) {
        ReturnRequestEntity request = lock(id);
        require(request, "refund", ReturnStatus.RECEIVED);
        request.setRefundAmount(estimatedRefund(request));
        request.setStatus(ReturnStatus.REFUNDED);
        request.setRefundedAt(now());
        return saveAndNotify(request);
    }

    static BigDecimal estimatedRefund(ReturnRequestEntity request) {
        BigDecimal itemsValue = request.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        OrderEntity order = request.getOrder();
        return CouponPricing.proportionalRefund(itemsValue, order.getTotalAmount(), order.getSubtotalAmount());
    }

    private ReturnDto saveAndNotify(ReturnRequestEntity request) {
        ReturnDto result = toDto(returnRepository.saveAndFlush(request));
        eventPublisher.publishEvent(new ReturnStatusChangedEvent(request.getId()));
        return result;
    }

    private ReturnRequestEntity lock(Long id) {
        return returnRepository.findByIdForUpdate(id).orElseThrow(this::notFound);
    }

    private static void require(ReturnRequestEntity request, String action, ReturnStatus expected) {
        if (request.getStatus() != expected) {
            throw new CustomException(
                    "Cannot " + action + " a return in status " + request.getStatus(), HttpStatus.CONFLICT);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(commerceClock);
    }

    private CustomException notFound() {
        return new CustomException("Return not found", HttpStatus.NOT_FOUND);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ReturnDto toDto(ReturnRequestEntity request) {
        return ReturnDto.builder()
                .id(request.getId())
                .orderId(request.getOrder().getId())
                .username(request.getUsername())
                .status(request.getStatus())
                .reason(request.getReason())
                .comment(request.getComment())
                .resolutionNote(request.getResolutionNote())
                .estimatedRefund(estimatedRefund(request))
                .refundAmount(request.getRefundAmount())
                .items(request.getItems().stream().map(item -> ReturnItemDto.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .condition(item.getCondition())
                        .build()).toList())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .approvedAt(request.getApprovedAt())
                .rejectedAt(request.getRejectedAt())
                .receivedAt(request.getReceivedAt())
                .refundedAt(request.getRefundedAt())
                .build();
    }
}
