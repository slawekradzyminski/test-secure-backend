package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryMovementEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.awesome.testing.internal.dto.InventoryReceiptDto;
import com.awesome.testing.internal.dto.InventoryReceiptLineDto;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.inventory.InventoryMovementRepository;
import com.awesome.testing.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalInventoryServiceTest {

    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private InventoryMovementRepository movementRepository;
    @Mock private InventoryService inventoryService;

    private InternalInventoryService service;
    private ProductEntity first;
    private ProductEntity second;

    @BeforeEach
    void setUp() {
        service = new InternalInventoryService(productRepository, orderRepository, movementRepository, inventoryService);
        first = ProductEntity.builder().id(1L).name("A").stockQuantity(0).build();
        second = ProductEntity.builder().id(2L).name("B").stockQuantity(0).build();
        lenient().when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(first));
        lenient().when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
    }

    @Test
    void locksProductsInIdOrderBeforeCheckingForAReplayAndReturnsIdsInLineOrder() {
        when(movementRepository.findByRequestId(REQUEST)).thenReturn(List.of());
        when(inventoryService.receive(eq(first), eq(5), any(), any(), any(), any(), eq(REQUEST)))
                .thenReturn(movement(11L, first, 5, InventoryMovementType.PURCHASE_RECEIVED));
        when(inventoryService.receive(eq(second), eq(3), any(), any(), any(), any(), eq(REQUEST)))
                .thenReturn(movement(12L, second, 3, InventoryMovementType.PURCHASE_RECEIVED));

        var result = service.receive(receipt(InventoryMovementType.PURCHASE_RECEIVED, line(2L, 3), line(1L, 5)));

        assertThat(result.movementIds()).containsExactly(12L, 11L);
        InOrder order = inOrder(productRepository, movementRepository, inventoryService);
        order.verify(productRepository).findByIdForUpdate(1L);
        order.verify(productRepository).findByIdForUpdate(2L);
        order.verify(movementRepository).findByRequestId(REQUEST);
        order.verify(inventoryService).receive(first, 5, InventoryMovementType.PURCHASE_RECEIVED, null,
                "warehouse-service", "PO-1", REQUEST);
    }

    @Test
    void replaysMatchingLinesWithoutTouchingStock() {
        when(movementRepository.findByRequestId(REQUEST)).thenReturn(List.of(
                movement(11L, first, 5, InventoryMovementType.RETURN_RESTOCKED),
                movement(12L, second, 3, InventoryMovementType.RETURN_RESTOCKED)));

        var result = service.receive(receipt(InventoryMovementType.RETURN_RESTOCKED, line(2L, 3), line(1L, 5)));

        assertThat(result.movementIds()).containsExactly(12L, 11L);
        verify(inventoryService, never()).receive(any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsReuseWithDifferentQuantitiesProductsOrType() {
        when(movementRepository.findByRequestId(REQUEST)).thenReturn(List.of(
                movement(11L, first, 5, InventoryMovementType.PURCHASE_RECEIVED)));

        assertConflict(receipt(InventoryMovementType.PURCHASE_RECEIVED, line(1L, 4)));
        assertConflict(receipt(InventoryMovementType.PURCHASE_RECEIVED, line(1L, 5), line(2L, 1)));
        assertConflict(receipt(InventoryMovementType.RETURN_RESTOCKED, line(1L, 5)));
        verify(inventoryService, never()).receive(any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsUnsupportedTypesAndDuplicateProductsBeforeLocking() {
        assertThatThrownBy(() -> service.receive(receipt(InventoryMovementType.ADMIN_ADJUSTMENT, line(1L, 1))))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.receive(receipt(InventoryMovementType.PURCHASE_RECEIVED, line(1L, 1), line(1L, 1))))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void unknownOrderOrProductIsNotFound() {
        when(orderRepository.findById(9L)).thenReturn(Optional.empty());
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.receive(new InventoryReceiptDto(REQUEST, InventoryMovementType.RETURN_RESTOCKED, 9L,
                "RMA", List.of(line(1L, 1)))))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.receive(receipt(InventoryMovementType.PURCHASE_RECEIVED, line(3L, 1))))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void productsAreReturnedByIdAscendingForAllOrSelectedIds() {
        when(productRepository.findAll(org.springframework.data.domain.Sort.by("id"))).thenReturn(List.of(first, second));
        when(productRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(second, first));

        assertThat(service.products(null)).extracting(product -> product.id()).containsExactly(1L, 2L);
        assertThat(service.products(List.of())).extracting(product -> product.id()).containsExactly(1L, 2L);
        assertThat(service.products(List.of(2L, 1L))).extracting(product -> product.id()).containsExactly(1L, 2L);
    }

    private void assertConflict(InventoryReceiptDto receipt) {
        assertThatThrownBy(() -> service.receive(receipt))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static InventoryReceiptDto receipt(InventoryMovementType type, InventoryReceiptLineDto... lines) {
        return new InventoryReceiptDto(REQUEST, type, null, " PO-1 ", List.of(lines));
    }

    private static InventoryReceiptLineDto line(Long productId, int quantity) {
        return new InventoryReceiptLineDto(productId, quantity);
    }

    private static InventoryMovementEntity movement(Long id, ProductEntity product, int delta, InventoryMovementType type) {
        return InventoryMovementEntity.builder().id(id).product(product).delta(delta).type(type).requestId(REQUEST).build();
    }
}
