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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final Long PRODUCT_ID = 1L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private ProductEntity product;

    @BeforeEach
    void setUp() {
        product = ProductEntity.builder().id(PRODUCT_ID).name("Keyboard").stockQuantity(5).build();
    }

    @Test
    void adjustLocksProductBeforeCheckingIdempotencyAndPersistsTheNewQuantity() {
        InventoryAdjustmentDto request = request(3, "delivery");
        InventoryMovementEntity movement = InventoryMovementEntity.builder()
                .id(9L)
                .product(product)
                .delta(3)
                .quantityAfter(8)
                .actor("admin")
                .reason("delivery")
                .requestId(request.getRequestId())
                .build();
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(movementRepository.findByProductIdAndRequestId(PRODUCT_ID, request.getRequestId()))
                .thenReturn(Optional.empty());
        when(movementRepository.save(any(InventoryMovementEntity.class))).thenReturn(movement);

        InventoryMovementDto result = inventoryService.adjust(PRODUCT_ID, request, "admin");

        assertThat(result.getQuantityAfter()).isEqualTo(8);
        assertThat(product.getStockQuantity()).isEqualTo(8);
        InOrder order = inOrder(productRepository, movementRepository);
        order.verify(productRepository).findByIdForUpdate(PRODUCT_ID);
        order.verify(movementRepository).findByProductIdAndRequestId(PRODUCT_ID, request.getRequestId());
        verify(productRepository).save(product);
    }

    @Test
    void adjustReplaysAnIdenticalRequestWithoutChangingStock() {
        InventoryAdjustmentDto request = request(2, "delivery");
        InventoryMovementEntity existing = InventoryMovementEntity.builder()
                .id(9L)
                .product(product)
                .delta(2)
                .quantityAfter(7)
                .actor("admin")
                .reason("delivery")
                .requestId(request.getRequestId())
                .build();
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(movementRepository.findByProductIdAndRequestId(PRODUCT_ID, request.getRequestId()))
                .thenReturn(Optional.of(existing));

        InventoryMovementDto result = inventoryService.adjust(PRODUCT_ID, request, "admin");

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(product.getStockQuantity()).isEqualTo(5);
        verify(productRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void adjustRejectsAConflictingRequestReuseWithoutSaving() {
        InventoryAdjustmentDto request = request(2, "delivery");
        InventoryMovementEntity existing = InventoryMovementEntity.builder()
                .product(product)
                .delta(1)
                .quantityAfter(6)
                .reason("delivery")
                .requestId(request.getRequestId())
                .build();
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(movementRepository.findByProductIdAndRequestId(PRODUCT_ID, request.getRequestId()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID, request, "admin"))
                .isInstanceOf(CustomException.class)
                .hasMessage("requestId already used with different payload");

        verify(productRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void adjustRejectsBelowZeroWithoutPersistingAnything() {
        InventoryAdjustmentDto request = request(-6, "correction");
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(movementRepository.findByProductIdAndRequestId(PRODUCT_ID, request.getRequestId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID, request, "admin"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Insufficient stock");

        assertThat(product.getStockQuantity()).isEqualTo(5);
        verify(productRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void adjustAllowsTheExactZeroBoundary() {
        InventoryAdjustmentDto request = request(-5, "sold at counter");
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(movementRepository.findByProductIdAndRequestId(PRODUCT_ID, request.getRequestId()))
                .thenReturn(Optional.empty());
        when(movementRepository.save(any(InventoryMovementEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovementDto result = inventoryService.adjust(PRODUCT_ID, request, "admin");

        assertThat(result.getQuantityAfter()).isZero();
        assertThat(product.getStockQuantity()).isZero();
        verify(productRepository).save(product);
    }

    @Test
    void adjustAllowsTheExactIntegerMaximumBoundary() {
        product.setStockQuantity(1);
        InventoryAdjustmentDto request = request(Integer.MAX_VALUE - 1, "bulk import");
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(movementRepository.findByProductIdAndRequestId(PRODUCT_ID, request.getRequestId()))
                .thenReturn(Optional.empty());
        when(movementRepository.save(any(InventoryMovementEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovementDto result = inventoryService.adjust(PRODUCT_ID, request, "admin");

        assertThat(result.getQuantityAfter()).isEqualTo(Integer.MAX_VALUE);
        assertThat(product.getStockQuantity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void deductAndRestorePersistAuditableOrderMovements() {
        OrderEntity order = OrderEntity.builder().id(7L).username("buyer").build();
        when(movementRepository.save(any(InventoryMovementEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovementEntity deducted = inventoryService.deduct(product, 5, order);

        assertThat(product.getStockQuantity()).isZero();
        assertThat(deducted.getType()).isEqualTo(InventoryMovementType.ORDER_DEDUCTED);
        assertThat(deducted.getDelta()).isEqualTo(-5);
        assertThat(deducted.getQuantityAfter()).isZero();
        assertThat(deducted.getOrder()).isSameAs(order);

        InventoryMovementEntity restored = inventoryService.restore(product, 5, order);

        assertThat(product.getStockQuantity()).isEqualTo(5);
        assertThat(restored.getType()).isEqualTo(InventoryMovementType.ORDER_RESTORED);
        assertThat(restored.getDelta()).isEqualTo(5);
        assertThat(restored.getQuantityAfter()).isEqualTo(5);
        verify(productRepository, org.mockito.Mockito.times(2)).save(product);
    }

    @Test
    void availabilityAcceptsBoundariesAndRejectsInvalidQuantities() {
        inventoryService.checkAvailable(product, 0);
        inventoryService.checkAvailable(product, 5);

        assertThatThrownBy(() -> inventoryService.checkAvailable(product, -1))
                .isInstanceOf(CustomException.class)
                .hasMessage("Insufficient stock for product 1");
        assertThatThrownBy(() -> inventoryService.checkAvailable(product, 6))
                .isInstanceOf(CustomException.class)
                .hasMessage("Insufficient stock for product 1");
    }

    @Test
    void getClassifiesOutOfStockLowStockAndInStockBoundaries() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        product.setStockQuantity(0);
        InventoryItemDto outOfStock = inventoryService.get(PRODUCT_ID, 1);
        product.setStockQuantity(1);
        InventoryItemDto lowStock = inventoryService.get(PRODUCT_ID, 1);
        product.setStockQuantity(2);
        InventoryItemDto inStock = inventoryService.get(PRODUCT_ID, 1);

        assertThat(outOfStock.getStockStatus()).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(lowStock.getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(inStock.getStockStatus()).isEqualTo(StockStatus.IN_STOCK);
        assertThatThrownBy(() -> inventoryService.get(PRODUCT_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lowStockThreshold must be at least 1");
    }

    private InventoryAdjustmentDto request(int delta, String reason) {
        return InventoryAdjustmentDto.builder()
                .delta(delta)
                .reason(reason)
                .requestId(UUID.randomUUID())
                .build();
    }
}
