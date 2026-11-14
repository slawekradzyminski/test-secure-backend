package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.inventory.InventoryItemDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.inventory.StockStatus;
import com.awesome.testing.dto.stock.AdjustStockRequestDto;
import com.awesome.testing.dto.stock.LedgerMovementDto;
import com.awesome.testing.service.stock.StockLedgerClient;
import com.awesome.testing.service.stock.StockLedgerRejection;
import com.awesome.testing.service.stock.StockProjection;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Inventory facade. warehouse-service owns the stock ledger (docs/ARCHITECTURE_DECISIONS.md ADR 6):
 * reads come from the {@code products.stock_quantity} projection, stock commands go to
 * warehouse-service through {@link StockLedgerClient} and their replies update the projection
 * through {@link StockProjection}.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_LOW_STOCK_THRESHOLD = 1;

    private final ProductRepository productRepository;
    private final StockLedgerClient stockLedger;
    private final StockProjection projection;

    @Transactional(readOnly = true)
    public Page<InventoryItemDto> list(
            int page,
            int size,
            String search,
            String category,
            StockStatus status,
            int threshold) {
        validateThreshold(threshold);
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by("id").ascending());

        Page<ProductEntity> result = productRepository.findAll((root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("name")),
                        "%" + search.toLowerCase(Locale.ROOT) + "%"));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("category")),
                        category.toLowerCase(Locale.ROOT)));
            }
            if (status == StockStatus.OUT_OF_STOCK) {
                predicates.add(criteriaBuilder.equal(root.get("stockQuantity"), 0));
            } else if (status == StockStatus.LOW_STOCK) {
                predicates.add(criteriaBuilder.between(root.get("stockQuantity"), 1, threshold));
            } else if (status == StockStatus.IN_STOCK) {
                predicates.add(criteriaBuilder.greaterThan(root.get("stockQuantity"), threshold));
            }
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        }, pageable);

        return result.map(product -> toItemDto(product, threshold));
    }

    @Transactional(readOnly = true)
    public InventoryItemDto get(Long productId, int threshold) {
        validateThreshold(threshold);
        return toItemDto(productRepository.findById(productId)
                .orElseThrow(this::productNotFound), threshold);
    }

    /**
     * Admin adjustment (REST, GraphQL, gRPC, product update, CSV import; contract 4.2): validates as
     * before, checks that the product exists, records the movement in warehouse-service's ledger and
     * applies the returned level to the projection. Must be called with no transaction open.
     */
    public InventoryMovementDto adjust(
            Long productId,
            InventoryAdjustmentDto request,
            String actor) {
        if (request.getDelta() == null || request.getDelta() == 0) {
            throw new CustomException("Inventory delta must not be zero", HttpStatus.BAD_REQUEST);
        }
        if (request.getRequestId() == null) {
            throw new CustomException("Inventory requestId is required", HttpStatus.BAD_REQUEST);
        }
        if (!productRepository.existsById(productId)) {
            throw productNotFound();
        }
        LedgerMovementDto movement;
        try {
            movement = stockLedger.adjust(new AdjustStockRequestDto(
                    productId, request.getDelta(), request.getReason(), request.getRequestId(), actor));
        } catch (StockLedgerRejection rejection) {
            if (rejection.isNotFound()) {
                throw new CustomException("Product not found", HttpStatus.NOT_FOUND, rejection);
            }
            throw new CustomException(rejection.getMessage() == null
                    ? "Inventory adjustment conflicts with current state"
                    : rejection.getMessage(), HttpStatus.CONFLICT, rejection);
        }
        if (movement.ledgerVersion() != null && movement.quantityAfter() != null) {
            projection.apply(productId, movement.quantityAfter(), movement.ledgerVersion());
        }
        return movement.toMovement();
    }

    /**
     * A product's movement history from warehouse-service's ledger, newest first. 404 when the
     * product does not exist here. Must be called with no transaction open.
     */
    public Page<InventoryMovementDto> movements(Long productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw productNotFound();
        }
        try {
            return stockLedger.movements(productId, pageable.getPageNumber(), pageable.getPageSize());
        } catch (StockLedgerRejection rejection) {
            // The product exists but warehouse-service has no stock item yet (its initial stock
            // command is still in the outbox): no movements so far.
            if (rejection.isNotFound()) {
                return Page.empty(pageable);
            }
            throw new CustomException(rejection.getMessage(), HttpStatus.CONFLICT, rejection);
        }
    }

    public void checkAvailable(ProductEntity product, int quantity) {
        if (quantity < 0 || quantity > product.getStockQuantity()) {
            throw conflict("Insufficient stock for product " + product.getId());
        }
    }

    private InventoryItemDto toItemDto(ProductEntity product, int threshold) {
        return InventoryItemDto.builder()
                .productId(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .availableQuantity(product.getStockQuantity())
                .stockStatus(classifyStock(product.getStockQuantity(), threshold))
                .lastChangedAt(product.getUpdatedAt())
                .build();
    }

    private StockStatus classifyStock(int quantity, int threshold) {
        if (quantity == 0) {
            return StockStatus.OUT_OF_STOCK;
        }
        return quantity <= threshold ? StockStatus.LOW_STOCK : StockStatus.IN_STOCK;
    }

    private void validateThreshold(int threshold) {
        if (threshold < MIN_LOW_STOCK_THRESHOLD) {
            throw new IllegalArgumentException("lowStockThreshold must be at least 1");
        }
    }

    private CustomException productNotFound() {
        return new CustomException("Product not found", HttpStatus.NOT_FOUND);
    }

    private CustomException conflict(String message) {
        return new CustomException(message, HttpStatus.CONFLICT);
    }
}
