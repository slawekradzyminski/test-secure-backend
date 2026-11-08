package com.awesome.testing.service.purchasing;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.controller.exception.ProductNotFoundException;
import com.awesome.testing.dto.purchasing.PurchaseOrderDto;
import com.awesome.testing.dto.purchasing.PurchaseOrderLineDto;
import com.awesome.testing.dto.purchasing.PurchaseOrderLineRequestDto;
import com.awesome.testing.dto.purchasing.PurchaseOrderRequestDto;
import com.awesome.testing.dto.purchasing.ReceiptLineDto;
import com.awesome.testing.dto.purchasing.ReceiptRequestDto;
import com.awesome.testing.dto.purchasing.ReorderSuggestionDto;
import com.awesome.testing.dto.purchasing.SupplierDto;
import com.awesome.testing.dto.purchasing.SupplierRequestDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.awesome.testing.entity.purchasing.PurchaseOrderEntity;
import com.awesome.testing.entity.purchasing.PurchaseOrderLineEntity;
import com.awesome.testing.entity.purchasing.PurchaseOrderStatus;
import com.awesome.testing.entity.purchasing.PurchaseReceiptEntity;
import com.awesome.testing.entity.purchasing.SupplierEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.purchasing.PurchaseOrderRepository;
import com.awesome.testing.repository.purchasing.PurchaseReceiptRepository;
import com.awesome.testing.repository.purchasing.SupplierRepository;
import com.awesome.testing.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchasingService {

    static final int REORDER_MULTIPLIER = 4;
    private static final int MIN_THRESHOLD = 1;
    private static final Set<PurchaseOrderStatus> INCOMING =
            Set.of(PurchaseOrderStatus.SUBMITTED, PurchaseOrderStatus.PARTIALLY_RECEIVED);

    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseReceiptRepository receiptRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final Clock commerceClock;

    @Transactional(readOnly = true)
    public List<SupplierDto> suppliers() {
        return supplierRepository.findAll(Sort.by("name")).stream().map(PurchasingService::toDto).toList();
    }

    @Transactional
    public SupplierDto createSupplier(SupplierRequestDto request) {
        String name = request.getName().trim();
        if (supplierRepository.existsByNameIgnoreCase(name)) {
            throw new CustomException("Supplier name already exists", HttpStatus.CONFLICT);
        }
        return toDto(supplierRepository.saveAndFlush(SupplierEntity.builder()
                .name(name)
                .email(request.getEmail().trim())
                .leadTimeDays(request.getLeadTimeDays())
                .build()));
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderDto> list(PurchaseOrderStatus status, Pageable pageable) {
        Page<PurchaseOrderEntity> page = status == null
                ? purchaseOrderRepository.findAllBy(pageable)
                : purchaseOrderRepository.findByStatus(status, pageable);
        return page.map(PurchasingService::toDto);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDto get(Long id) {
        return toDto(purchaseOrderRepository.findById(id).orElseThrow(this::notFound));
    }

    @Transactional
    public PurchaseOrderDto create(PurchaseOrderRequestDto request, String actor) {
        PurchaseOrderEntity purchaseOrder = PurchaseOrderEntity.builder()
                .status(PurchaseOrderStatus.DRAFT)
                .createdBy(actor)
                .build();
        applyDraft(request, purchaseOrder);
        return toDto(purchaseOrderRepository.saveAndFlush(purchaseOrder));
    }

    @Transactional
    public PurchaseOrderDto update(Long id, PurchaseOrderRequestDto request) {
        PurchaseOrderEntity purchaseOrder = lock(id);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new CustomException("Only draft purchase orders can be edited", HttpStatus.CONFLICT);
        }
        purchaseOrder.getLines().clear();
        purchaseOrderRepository.flush();
        applyDraft(request, purchaseOrder);
        return toDto(purchaseOrderRepository.saveAndFlush(purchaseOrder));
    }

    @Transactional
    public PurchaseOrderDto submit(Long id) {
        PurchaseOrderEntity purchaseOrder = lock(id);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new CustomException("Only draft purchase orders can be submitted", HttpStatus.CONFLICT);
        }
        if (purchaseOrder.getLines().isEmpty()) {
            throw new CustomException("A purchase order needs at least one line", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime now = LocalDateTime.now(commerceClock);
        purchaseOrder.setStatus(PurchaseOrderStatus.SUBMITTED);
        purchaseOrder.setSubmittedAt(now);
        purchaseOrder.setExpectedAt(now.plusDays(purchaseOrder.getSupplier().getLeadTimeDays()));
        return toDto(purchaseOrderRepository.saveAndFlush(purchaseOrder));
    }

    @Transactional
    public PurchaseOrderDto cancel(Long id) {
        PurchaseOrderEntity purchaseOrder = lock(id);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT
                && purchaseOrder.getStatus() != PurchaseOrderStatus.SUBMITTED) {
            throw new CustomException("Only draft or submitted purchase orders can be cancelled", HttpStatus.CONFLICT);
        }
        purchaseOrder.setStatus(PurchaseOrderStatus.CANCELLED);
        purchaseOrder.setCancelledAt(LocalDateTime.now(commerceClock));
        return toDto(purchaseOrderRepository.saveAndFlush(purchaseOrder));
    }

    /**
     * Books received goods. The purchase order is locked first and products
     * are locked in ascending id order, matching checkout's lock order.
     */
    @Transactional
    public PurchaseOrderDto receive(Long id, ReceiptRequestDto request, String actor) {
        PurchaseOrderEntity purchaseOrder = lock(id);
        Map<Long, Integer> quantities = new TreeMap<>();
        for (ReceiptLineDto line : request.getLines()) {
            if (quantities.put(line.getProductId(), line.getQuantity()) != null) {
                throw new CustomException(
                        "Product " + line.getProductId() + " is listed more than once", HttpStatus.BAD_REQUEST);
            }
        }
        String fingerprint = fingerprint(quantities);
        Optional<PurchaseReceiptEntity> previous = receiptRepository.findByPurchaseOrderIdAndRequestId(id, request.getRequestId());
        if (previous.isPresent()) {
            if (!previous.get().getLinesFingerprint().equals(fingerprint)) {
                throw new CustomException("requestId already used with different lines", HttpStatus.CONFLICT);
            }
            return toDto(purchaseOrder);
        }
        if (!purchaseOrder.getStatus().isOpenForReceipt()) {
            throw new CustomException(
                    "Cannot receive goods for a purchase order in status " + purchaseOrder.getStatus(), HttpStatus.CONFLICT);
        }
        Map<Long, PurchaseOrderLineEntity> lines = purchaseOrder.getLines().stream()
                .collect(Collectors.toMap(line -> line.getProduct().getId(), line -> line));
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            PurchaseOrderLineEntity line = lines.get(entry.getKey());
            if (line == null) {
                throw new CustomException(
                        "Product " + entry.getKey() + " is not on this purchase order", HttpStatus.BAD_REQUEST);
            }
            if (entry.getValue() > line.outstandingQuantity()) {
                throw new CustomException("Receiving " + entry.getValue() + " units of product " + entry.getKey()
                        + " exceeds the outstanding quantity of " + line.outstandingQuantity(), HttpStatus.CONFLICT);
            }
        }
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            PurchaseOrderLineEntity line = lines.get(entry.getKey());
            ProductEntity product = productRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ProductNotFoundException("Product not found"));
            line.setReceivedQuantity(line.getReceivedQuantity() + entry.getValue());
            inventoryService.receive(product, entry.getValue(), InventoryMovementType.PURCHASE_RECEIVED,
                    null, actor, "Purchase order #" + purchaseOrder.getId());
        }
        boolean complete = purchaseOrder.getLines().stream().allMatch(line -> line.outstandingQuantity() == 0);
        purchaseOrder.setStatus(complete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        if (complete) {
            purchaseOrder.setReceivedAt(LocalDateTime.now(commerceClock));
        }
        receiptRepository.save(PurchaseReceiptEntity.builder()
                .purchaseOrder(purchaseOrder)
                .requestId(request.getRequestId())
                .linesFingerprint(fingerprint)
                .receivedBy(actor)
                .build());
        return toDto(purchaseOrderRepository.saveAndFlush(purchaseOrder));
    }

    @Transactional(readOnly = true)
    public List<ReorderSuggestionDto> reorderSuggestions(int threshold) {
        if (threshold < MIN_THRESHOLD) {
            throw new IllegalArgumentException("lowStockThreshold must be at least 1");
        }
        Map<Long, Integer> incoming = new HashMap<>();
        for (Object[] row : purchaseOrderRepository.sumOutstandingByProduct(INCOMING)) {
            incoming.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return productRepository.findAll(Sort.by("id")).stream()
                .map(product -> suggestion(product, incoming.getOrDefault(product.getId(), 0), threshold))
                .filter(suggestion -> suggestion != null)
                .sorted(Comparator.comparingInt((ReorderSuggestionDto s) -> s.getStockQuantity() + s.getIncomingQuantity())
                        .thenComparing(ReorderSuggestionDto::getProductId))
                .toList();
    }

    static ReorderSuggestionDto suggestion(ProductEntity product, int incoming, int threshold) {
        int projected = product.getStockQuantity() + incoming;
        if (projected > threshold) {
            return null;
        }
        return ReorderSuggestionDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .category(product.getCategory())
                .stockQuantity(product.getStockQuantity())
                .incomingQuantity(incoming)
                .suggestedQuantity(REORDER_MULTIPLIER * threshold - projected)
                .build();
    }

    private void applyDraft(PurchaseOrderRequestDto request, PurchaseOrderEntity purchaseOrder) {
        SupplierEntity supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new CustomException("Supplier not found", HttpStatus.NOT_FOUND));
        purchaseOrder.setSupplier(supplier);
        purchaseOrder.setNote(request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim());
        Set<Long> seen = new java.util.HashSet<>();
        for (PurchaseOrderLineRequestDto line : request.getLines() == null
                ? List.<PurchaseOrderLineRequestDto>of() : request.getLines()) {
            if (!seen.add(line.getProductId())) {
                throw new CustomException(
                        "Product " + line.getProductId() + " is listed more than once", HttpStatus.BAD_REQUEST);
            }
            ProductEntity product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException("Product not found"));
            purchaseOrder.addLine(PurchaseOrderLineEntity.builder()
                    .product(product)
                    .orderedQuantity(line.getQuantity())
                    .receivedQuantity(0)
                    .unitCost(line.getUnitCost())
                    .build());
        }
    }

    static String fingerprint(Map<Long, Integer> quantities) {
        String canonical = quantities.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private PurchaseOrderEntity lock(Long id) {
        return purchaseOrderRepository.findByIdForUpdate(id).orElseThrow(this::notFound);
    }

    private CustomException notFound() {
        return new CustomException("Purchase order not found", HttpStatus.NOT_FOUND);
    }

    private static SupplierDto toDto(SupplierEntity supplier) {
        return SupplierDto.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .email(supplier.getEmail())
                .leadTimeDays(supplier.getLeadTimeDays())
                .createdAt(supplier.getCreatedAt())
                .build();
    }

    private static PurchaseOrderDto toDto(PurchaseOrderEntity purchaseOrder) {
        List<PurchaseOrderLineDto> lines = purchaseOrder.getLines().stream().map(line -> PurchaseOrderLineDto.builder()
                .productId(line.getProduct().getId())
                .productName(line.getProduct().getName())
                .orderedQuantity(line.getOrderedQuantity())
                .receivedQuantity(line.getReceivedQuantity())
                .outstandingQuantity(line.outstandingQuantity())
                .unitCost(line.getUnitCost())
                .lineTotal(line.getUnitCost().multiply(BigDecimal.valueOf(line.getOrderedQuantity())))
                .build()).toList();
        return PurchaseOrderDto.builder()
                .id(purchaseOrder.getId())
                .supplierId(purchaseOrder.getSupplier().getId())
                .supplierName(purchaseOrder.getSupplier().getName())
                .status(purchaseOrder.getStatus())
                .note(purchaseOrder.getNote())
                .createdBy(purchaseOrder.getCreatedBy())
                .totalCost(lines.stream().map(PurchaseOrderLineDto::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                .lines(lines)
                .createdAt(purchaseOrder.getCreatedAt())
                .updatedAt(purchaseOrder.getUpdatedAt())
                .submittedAt(purchaseOrder.getSubmittedAt())
                .expectedAt(purchaseOrder.getExpectedAt())
                .receivedAt(purchaseOrder.getReceivedAt())
                .cancelledAt(purchaseOrder.getCancelledAt())
                .build();
    }
}
