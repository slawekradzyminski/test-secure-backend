package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.inventory.InventoryItemDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.inventory.StockStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryMovementEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.inventory.InventoryMovementRepository;
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
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_LOW_STOCK_THRESHOLD = 1;

    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

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

    @Transactional
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

        ProductEntity product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(this::productNotFound);
        Optional<InventoryMovementEntity> priorMovement = movementRepository.findByProductIdAndRequestId(
                productId, request.getRequestId());
        if (priorMovement.isPresent()) {
            InventoryMovementEntity movement = priorMovement.get();
            if (!Objects.equals(movement.getDelta(), request.getDelta())
                    || !Objects.equals(movement.getReason(), request.getReason())) {
                throw conflict("requestId already used with different payload");
            }
            return toMovementDto(movement);
        }

        long resultingQuantity = (long) product.getStockQuantity() + request.getDelta();
        if (resultingQuantity < 0) {
            throw conflict("Insufficient stock");
        }
        if (resultingQuantity > Integer.MAX_VALUE) {
            throw conflict("Inventory quantity exceeds supported maximum");
        }

        product.setStockQuantity((int) resultingQuantity);
        productRepository.save(product);
        return toMovementDto(movementRepository.save(InventoryMovementEntity.builder()
                .product(product)
                .type(InventoryMovementType.ADMIN_ADJUSTMENT)
                .delta(request.getDelta())
                .quantityAfter((int) resultingQuantity)
                .actor(actor)
                .reason(request.getReason())
                .requestId(request.getRequestId())
                .build()));
    }

    @Transactional(readOnly = true)
    public Page<InventoryMovementDto> movements(Long productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw productNotFound();
        }
        return movementRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId, pageable)
                .map(this::toMovementDto);
    }

    @Transactional
    public InventoryMovementEntity initial(ProductEntity product, String actor) {
        if (product.getStockQuantity() == 0) {
            return null;
        }
        return movementRepository.save(InventoryMovementEntity.builder()
                .product(product)
                .type(InventoryMovementType.INITIAL_STOCK)
                .delta(product.getStockQuantity())
                .quantityAfter(product.getStockQuantity())
                .actor(actor)
                .reason("Initial stock")
                .build());
    }

    @Transactional
    public InventoryMovementEntity deduct(
            ProductEntity product,
            int quantity,
            OrderEntity order) {
        int resultingQuantity = product.getStockQuantity() - quantity;
        if (resultingQuantity < 0) {
            throw conflict("Insufficient stock for product " + product.getId());
        }
        product.setStockQuantity(resultingQuantity);
        productRepository.save(product);
        return movementRepository.save(InventoryMovementEntity.builder()
                .product(product)
                .order(order)
                .type(InventoryMovementType.ORDER_DEDUCTED)
                .delta(-quantity)
                .quantityAfter(resultingQuantity)
                .actor(order.getUsername())
                .reason("Order deduction")
                .build());
    }

    @Transactional
    public InventoryMovementEntity restore(
            ProductEntity product,
            int quantity,
            OrderEntity order) {
        int resultingQuantity = Math.addExact(product.getStockQuantity(), quantity);
        product.setStockQuantity(resultingQuantity);
        productRepository.save(product);
        return movementRepository.save(InventoryMovementEntity.builder()
                .product(product)
                .order(order)
                .type(InventoryMovementType.ORDER_RESTORED)
                .delta(quantity)
                .quantityAfter(resultingQuantity)
                .actor(order.getUsername())
                .reason("Order cancellation")
                .build());
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

    private InventoryMovementDto toMovementDto(InventoryMovementEntity movement) {
        return InventoryMovementDto.builder()
                .id(movement.getId())
                .productId(movement.getProduct().getId())
                .orderId(movement.getOrder() == null ? null : movement.getOrder().getId())
                .type(movement.getType())
                .delta(movement.getDelta())
                .quantityAfter(movement.getQuantityAfter())
                .actor(movement.getActor())
                .reason(movement.getReason())
                .requestId(movement.getRequestId())
                .createdAt(movement.getCreatedAt())
                .build();
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
