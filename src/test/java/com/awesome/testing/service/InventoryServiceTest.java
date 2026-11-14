package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.inventory.InventoryItemDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.inventory.StockStatus;
import com.awesome.testing.dto.stock.AdjustStockRequestDto;
import com.awesome.testing.dto.stock.LedgerMovementDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.service.stock.StockLedgerClient;
import com.awesome.testing.service.stock.StockLedgerRejection;
import com.awesome.testing.service.stock.StockProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The inventory facade: validation, warehouse-service rejections and the projection update. */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final Long PRODUCT_ID = 1L;
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 14, 10, 0, 0, 123_456_000);

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockLedgerClient stockLedger;

    @Mock
    private StockProjection projection;

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
        verifyNoInteractions(productRepository);
    }

    @Test
    void adjustChecksTheProductThenRecordsInTheLedgerAndAppliesTheReturnedLevel() {
        InventoryAdjustmentDto request = request(3, "delivery");
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(stockLedger.adjust(any())).thenReturn(movement(request.getRequestId(), 3, 8, 43L));

        InventoryMovementDto result = inventoryService.adjust(PRODUCT_ID, request, "admin");

        assertThat(result).isEqualTo(InventoryMovementDto.builder().id(9L).productId(PRODUCT_ID)
                .type(InventoryMovementType.ADMIN_ADJUSTMENT).delta(3).quantityAfter(8).actor("admin")
                .reason("delivery").requestId(request.getRequestId()).createdAt(AT).build());
        InOrder order = inOrder(productRepository, stockLedger, projection);
        order.verify(productRepository).existsById(PRODUCT_ID);
        order.verify(stockLedger).adjust(new AdjustStockRequestDto(PRODUCT_ID, 3, "delivery",
                request.getRequestId(), "admin"));
        order.verify(projection).apply(PRODUCT_ID, 8, 43L);
    }

    @Test
    void aMovementWithoutALedgerVersionLeavesTheProjectionAlone() {
        InventoryAdjustmentDto request = request(3, "delivery");
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(stockLedger.adjust(any())).thenReturn(movement(request.getRequestId(), 3, 8, null));

        assertThat(inventoryService.adjust(PRODUCT_ID, request, "admin").getQuantityAfter()).isEqualTo(8);

        verifyNoInteractions(projection);
    }

    @Test
    void adjustValidatesBeforeCallingWarehouseService() {
        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID, request(0, "x"), "admin"))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Inventory delta must not be zero");
                });
        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID,
                InventoryAdjustmentDto.builder().reason("x").requestId(UUID.randomUUID()).build(), "admin"))
                .hasMessage("Inventory delta must not be zero");
        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID,
                InventoryAdjustmentDto.builder().delta(1).reason("x").build(), "admin"))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Inventory requestId is required");
                });
        verifyNoInteractions(productRepository, stockLedger, projection);
    }

    @Test
    void adjustOfAnUnknownProductIsNotFoundWithoutCallingWarehouseService() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(false);

        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID, request(1, "x"), "admin"))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Product not found");
                });
        verifyNoInteractions(stockLedger);
    }

    @Test
    void ledgerConflictsKeepWarehouseServicesMessageAndChangeNothing() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(stockLedger.adjust(any())).thenThrow(new StockLedgerRejection(409, "Insufficient stock", null));

        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID, request(-6, "x"), "admin"))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Insufficient stock");
                });
        verifyNoInteractions(projection);
    }

    @Test
    void aConflictWithoutAMessageGetsAGenericOne() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(stockLedger.adjust(any())).thenThrow(new StockLedgerRejection(409, null, null));

        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID, request(-6, "x"), "admin"))
                .hasMessage("Inventory adjustment conflicts with current state");
    }

    @Test
    void aProductWithoutAStockItemIsNotFound() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(stockLedger.adjust(any())).thenThrow(new StockLedgerRejection(404, "Product not found", null));

        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID, request(1, "x"), "admin"))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Product not found");
                });
        verifyNoInteractions(projection);
    }

    @Test
    void anUnavailableWarehousePropagatesThe503() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(stockLedger.adjust(any())).thenThrow(
                new CustomException(StockLedgerClient.UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID, request(1, "x"), "admin"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(projection);
    }

    @Test
    void movementsComeFromWarehouseServiceAfterTheProductCheck() {
        Page<InventoryMovementDto> page = new PageImpl<>(List.of(InventoryMovementDto.builder().id(4L).build()),
                PageRequest.of(1, 3), 4);
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(stockLedger.movements(PRODUCT_ID, 1, 3)).thenReturn(page);

        assertThat(inventoryService.movements(PRODUCT_ID, PageRequest.of(1, 3))).isSameAs(page);
    }

    @Test
    void movementsOfAnUnknownProductAreNotFoundAndOfAProductWithoutStockItemEmpty() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(false);
        assertThatThrownBy(() -> inventoryService.movements(PRODUCT_ID, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(stockLedger, never()).movements(anyLong(), anyInt(), anyInt());

        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(stockLedger.movements(PRODUCT_ID, 0, 20)).thenThrow(new StockLedgerRejection(404, "missing", PRODUCT_ID));
        Page<InventoryMovementDto> empty = inventoryService.movements(PRODUCT_ID, PageRequest.of(0, 20));
        assertThat(empty.getContent()).isEmpty();
        assertThat(empty.getTotalElements()).isZero();
        assertThat(empty.getPageable()).isEqualTo(PageRequest.of(0, 20));

        org.mockito.Mockito.doThrow(new StockLedgerRejection(409, "odd", null))
                .when(stockLedger).movements(PRODUCT_ID, 0, 20);
        assertThatThrownBy(() -> inventoryService.movements(PRODUCT_ID, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT));
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

    private static LedgerMovementDto movement(UUID requestId, int delta, int after, Long version) {
        return new LedgerMovementDto(9L, PRODUCT_ID, null, InventoryMovementType.ADMIN_ADJUSTMENT, delta, after,
                "admin", "delivery", requestId, AT, version);
    }

    private static InventoryAdjustmentDto request(int delta, String reason) {
        return InventoryAdjustmentDto.builder()
                .delta(delta)
                .reason(reason)
                .requestId(UUID.randomUUID())
                .build();
    }
}
