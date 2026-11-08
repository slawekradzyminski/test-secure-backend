package com.awesome.testing.service;

import org.springframework.context.ApplicationEventPublisher;
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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InventoryService inventoryService;

    private ProductEntity product;

    @BeforeEach
    void setUp() {
        product = ProductEntity.builder().id(PRODUCT_ID).name("Keyboard").stockQuantity(5).build();
    }

    @Test
    void listRejectsInvalidLowStockThresholdBeforeQueryingProducts() {
        assertThatThrownBy(() -> inventoryService.list(0, 10, null, null, StockStatus.LOW_STOCK, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lowStockThreshold must be at least 1");

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

    @Test
    void replenishingASoldOutProductPublishesOneEventButTopUpsDoNot() {
        product.setStockQuantity(0);
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(java.util.Optional.of(product));
        when(movementRepository.save(any(InventoryMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.adjust(PRODUCT_ID, com.awesome.testing.dto.inventory.InventoryAdjustmentDto.builder()
                .delta(1).reason("restock").requestId(java.util.UUID.randomUUID()).build(), "admin");
        inventoryService.adjust(PRODUCT_ID, com.awesome.testing.dto.inventory.InventoryAdjustmentDto.builder()
                .delta(4).reason("restock").requestId(java.util.UUID.randomUUID()).build(), "admin");

        verify(eventPublisher, org.mockito.Mockito.times(1))
                .publishEvent(new com.awesome.testing.service.notification.StockReplenishedEvent(PRODUCT_ID));
    }

    @Test
    void sellingOutAndRestoringFromZeroPublishesTheReplenishment() {
        OrderEntity order = OrderEntity.builder().id(7L).username("buyer").build();
        when(movementRepository.save(any(InventoryMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.deduct(product, 5, order);
        org.mockito.Mockito.verifyNoInteractions(eventPublisher);
        inventoryService.restore(product, 2, order);

        verify(eventPublisher).publishEvent(new com.awesome.testing.service.notification.StockReplenishedEvent(PRODUCT_ID));
    }

    @Test
    void receivingGoodsAddsStockAndRejectsUnsupportedOrEmptyReceipts() {
        product.setStockQuantity(0);
        when(movementRepository.save(any(InventoryMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovementEntity movement = inventoryService.receive(product, 3,
                InventoryMovementType.PURCHASE_RECEIVED, null, "admin", "Purchase order #1");

        assertThat(product.getStockQuantity()).isEqualTo(3);
        assertThat(movement.getDelta()).isEqualTo(3);
        assertThat(movement.getQuantityAfter()).isEqualTo(3);
        assertThat(movement.getType()).isEqualTo(InventoryMovementType.PURCHASE_RECEIVED);
        verify(eventPublisher).publishEvent(new com.awesome.testing.service.notification.StockReplenishedEvent(PRODUCT_ID));
        assertThatThrownBy(() -> inventoryService.receive(product, 0,
                InventoryMovementType.PURCHASE_RECEIVED, null, "admin", "x")).hasMessage("Received quantity must be positive");
        assertThatThrownBy(() -> inventoryService.receive(product, 1,
                InventoryMovementType.ADMIN_ADJUSTMENT, null, "admin", "x")).isInstanceOf(IllegalArgumentException.class);
        product.setStockQuantity(Integer.MAX_VALUE);
        assertThatThrownBy(() -> inventoryService.receive(product, 1,
                InventoryMovementType.RETURN_RESTOCKED, null, "admin", "x")).hasMessage("Inventory quantity exceeds supported maximum");
    }

    @Test
    void receiptsTagTheMovementWithTheCallersRequestId() {
        product.setStockQuantity(4);
        when(movementRepository.save(any(InventoryMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        java.util.UUID requestId = java.util.UUID.randomUUID();

        InventoryMovementEntity tagged = inventoryService.receive(product, 2,
                InventoryMovementType.RETURN_RESTOCKED, null, "warehouse-service", "RMA-3", requestId);
        InventoryMovementEntity untagged = inventoryService.receive(product, 1,
                InventoryMovementType.PURCHASE_RECEIVED, null, "admin", "PO-1");

        assertThat(tagged.getRequestId()).isEqualTo(requestId);
        assertThat(tagged.getActor()).isEqualTo("warehouse-service");
        assertThat(tagged.getReason()).isEqualTo("RMA-3");
        assertThat(untagged.getRequestId()).isNull();
        assertThat(product.getStockQuantity()).isEqualTo(7);
        org.mockito.Mockito.verifyNoInteractions(eventPublisher);
    }
}
