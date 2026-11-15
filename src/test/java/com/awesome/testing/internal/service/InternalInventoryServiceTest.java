package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryMovementEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.awesome.testing.internal.dto.InventoryExportDto;
import com.awesome.testing.internal.dto.InventoryExportMovementDto;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.inventory.InventoryMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalInventoryServiceTest {

    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Mock private ProductRepository productRepository;
    @Mock private InventoryMovementRepository movementRepository;

    private InternalInventoryService service;
    private ProductEntity first;
    private ProductEntity second;

    @BeforeEach
    void setUp() {
        service = new InternalInventoryService(productRepository, movementRepository);
        first = ProductEntity.builder().id(1L).name("A").stockQuantity(7).build();
        second = ProductEntity.builder().id(2L).name("B").stockQuantity(0).build();
    }

    @Test
    void productsAreReturnedByIdAscendingForAllOrSelectedIds() {
        when(productRepository.findAll(org.springframework.data.domain.Sort.by("id"))).thenReturn(List.of(first, second));
        when(productRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(second, first));

        assertThat(service.products(null)).extracting(product -> product.id()).containsExactly(1L, 2L);
        assertThat(service.products(List.of())).extracting(product -> product.id()).containsExactly(1L, 2L);
        assertThat(service.products(List.of(2L, 1L))).extracting(product -> product.id()).containsExactly(1L, 2L);
    }

    @Test
    void exportGroupsMovementsPerProductInIdOrderAndPointsAtTheNextPageWhenFull() {
        OrderEntity order = OrderEntity.builder().id(9L).build();
        LocalDateTime at = LocalDateTime.of(2026, 11, 14, 10, 0, 0, 123_456_000);
        when(productRepository.findAfterId(0L, PageRequest.of(0, 2))).thenReturn(List.of(first, second));
        when(movementRepository.findByProductIdsInIdOrder(List.of(1L, 2L))).thenReturn(List.of(
                movement(5L, first, null, InventoryMovementType.INITIAL_STOCK, 10, 10, null, at),
                movement(6L, first, order, InventoryMovementType.ORDER_DEDUCTED, -3, 7, REQUEST, at)));

        InventoryExportDto page = service.export(null, 2);

        assertThat(page.nextAfterProductId()).isEqualTo(2L);
        assertThat(page.items()).extracting(item -> item.productId()).containsExactly(1L, 2L);
        assertThat(page.items().getFirst().quantity()).isEqualTo(7);
        assertThat(page.items().getFirst().movements()).containsExactly(
                new InventoryExportMovementDto(5L, null, InventoryMovementType.INITIAL_STOCK, 10, 10, "admin",
                        "Initial stock", null, at),
                new InventoryExportMovementDto(6L, 9L, InventoryMovementType.ORDER_DEDUCTED, -3, 7, "admin",
                        "Initial stock", REQUEST, at));
        assertThat(page.items().get(1).quantity()).isZero();
        assertThat(page.items().get(1).movements()).isEmpty();
    }

    @Test
    void exportEndsWithANullCursorOnAPageThatIsNotFull() {
        when(productRepository.findAfterId(1L, PageRequest.of(0, 500))).thenReturn(List.of(second));
        when(movementRepository.findByProductIdsInIdOrder(List.of(2L))).thenReturn(List.of());

        InventoryExportDto page = service.export(1L, 500);

        assertThat(page.items()).extracting(item -> item.productId()).containsExactly(2L);
        assertThat(page.nextAfterProductId()).isNull();
    }

    @Test
    void anEmptyExportPageReadsNoMovements() {
        when(productRepository.findAfterId(2L, PageRequest.of(0, 1))).thenReturn(List.of());

        InventoryExportDto page = service.export(2L, 1);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextAfterProductId()).isNull();
        verify(movementRepository, never()).findByProductIdsInIdOrder(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 501})
    void exportRejectsALimitOutsideOneToFiveHundred(int limit) {
        assertThatThrownBy(() -> service.export(null, limit))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("limit must be between 1 and 500");
                });
        verify(productRepository, never()).findAfterId(anyLong(), any());
    }

    @Test
    void exportRejectsANegativeCursorButAcceptsTheEdges() {
        assertThatThrownBy(() -> service.export(-1L, 10))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        when(productRepository.findAfterId(0L, PageRequest.of(0, 1))).thenReturn(List.of());
        when(productRepository.findAfterId(0L, PageRequest.of(0, 500))).thenReturn(List.of());

        assertThat(service.export(0L, 1).items()).isEmpty();
        assertThat(service.export(0L, 500).items()).isEmpty();
    }

    private static InventoryMovementEntity movement(Long id, ProductEntity product, OrderEntity order,
                                                    InventoryMovementType type, int delta, int after, UUID requestId,
                                                    LocalDateTime createdAt) {
        return InventoryMovementEntity.builder().id(id).product(product).order(order).type(type).delta(delta)
                .quantityAfter(after).actor("admin").reason("Initial stock").requestId(requestId)
                .createdAt(createdAt).build();
    }
}
