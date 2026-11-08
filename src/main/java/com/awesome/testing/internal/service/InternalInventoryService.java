package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryMovementEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.awesome.testing.internal.dto.InternalProductDto;
import com.awesome.testing.internal.dto.InventoryReceiptDto;
import com.awesome.testing.internal.dto.InventoryReceiptLineDto;
import com.awesome.testing.internal.dto.InventoryReceiptResultDto;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.inventory.InventoryMovementRepository;
import com.awesome.testing.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Product snapshots and stock receipts for warehouse-service. */
@Service
@RequiredArgsConstructor
public class InternalInventoryService {

    static final String ACTOR = "warehouse-service";
    private static final Set<InventoryMovementType> RECEIPT_TYPES =
            Set.of(InventoryMovementType.PURCHASE_RECEIVED, InventoryMovementType.RETURN_RESTOCKED);

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public List<InternalProductDto> products(List<Long> ids) {
        List<ProductEntity> products = ids == null || ids.isEmpty()
                ? productRepository.findAll(Sort.by("id"))
                : productRepository.findAllById(ids).stream()
                        .sorted(Comparator.comparing(ProductEntity::getId))
                        .toList();
        return products.stream().map(InternalProductDto::from).toList();
    }

    /**
     * Adds the received quantities and tags every movement with the request id.
     * Products are locked in id order before the replay check, so a concurrent
     * retry waits for the first call and then replays its movements.
     */
    @Transactional
    public InventoryReceiptResultDto receive(InventoryReceiptDto receipt) {
        if (!RECEIPT_TYPES.contains(receipt.type())) {
            throw new CustomException("type must be PURCHASE_RECEIVED or RETURN_RESTOCKED", HttpStatus.BAD_REQUEST);
        }
        Map<Long, Integer> requested = requestedQuantities(receipt.lines());
        OrderEntity order = receipt.orderId() == null ? null : orderRepository.findById(receipt.orderId())
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
        Map<Long, ProductEntity> locked = new TreeMap<>();
        for (Long productId : requested.keySet()) {
            locked.put(productId, productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new CustomException("Product " + productId + " not found", HttpStatus.NOT_FOUND)));
        }

        List<InventoryMovementEntity> prior = movementRepository.findByRequestId(receipt.requestId());
        if (!prior.isEmpty()) {
            return replay(receipt, requested, prior);
        }

        Map<Long, Long> movementIds = new TreeMap<>();
        locked.forEach((productId, product) -> movementIds.put(productId, inventoryService.receive(
                product, requested.get(productId), receipt.type(), order, ACTOR, receipt.reference().trim(),
                receipt.requestId()).getId()));
        return inLineOrder(receipt.lines(), movementIds);
    }

    private InventoryReceiptResultDto replay(
            InventoryReceiptDto receipt, Map<Long, Integer> requested, List<InventoryMovementEntity> prior) {
        Map<Long, Integer> recorded = new TreeMap<>();
        Map<Long, Long> movementIds = new TreeMap<>();
        boolean sameType = true;
        for (InventoryMovementEntity movement : prior) {
            recorded.put(movement.getProduct().getId(), movement.getDelta());
            movementIds.put(movement.getProduct().getId(), movement.getId());
            sameType &= movement.getType() == receipt.type();
        }
        if (!sameType || !recorded.equals(requested)) {
            throw new CustomException("requestId already used with different lines", HttpStatus.CONFLICT);
        }
        return inLineOrder(receipt.lines(), movementIds);
    }

    private static Map<Long, Integer> requestedQuantities(List<InventoryReceiptLineDto> lines) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (InventoryReceiptLineDto line : lines) {
            if (quantities.putIfAbsent(line.productId(), line.quantity()) != null) {
                throw new CustomException("Each product may appear only once per receipt", HttpStatus.BAD_REQUEST);
            }
        }
        return quantities;
    }

    private static InventoryReceiptResultDto inLineOrder(List<InventoryReceiptLineDto> lines, Map<Long, Long> movementIds) {
        List<Long> ids = new ArrayList<>(lines.size());
        lines.forEach(line -> ids.add(movementIds.get(line.productId())));
        return new InventoryReceiptResultDto(ids);
    }
}
