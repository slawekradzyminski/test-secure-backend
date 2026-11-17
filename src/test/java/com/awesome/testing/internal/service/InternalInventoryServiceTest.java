package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.internal.dto.InventoryExportDto;
import com.awesome.testing.internal.dto.InventoryExportItemDto;
import com.awesome.testing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalInventoryServiceTest {

    @Mock private ProductRepository productRepository;

    private InternalInventoryService service;
    private ProductEntity first;
    private ProductEntity second;

    @BeforeEach
    void setUp() {
        service = new InternalInventoryService(productRepository);
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
    void exportListsProjectedQuantitiesInIdOrderAndPointsAtTheNextPageWhenFull() {
        when(productRepository.findAfterId(0L, PageRequest.of(0, 2))).thenReturn(List.of(first, second));

        InventoryExportDto page = service.export(null, 2);

        assertThat(page.nextAfterProductId()).isEqualTo(2L);
        assertThat(page.items()).containsExactly(
                new InventoryExportItemDto(1L, 7), new InventoryExportItemDto(2L, 0));
    }

    @Test
    void exportEndsWithANullCursorOnAPageThatIsNotFull() {
        when(productRepository.findAfterId(1L, PageRequest.of(0, 500))).thenReturn(List.of(second));

        InventoryExportDto page = service.export(1L, 500);

        assertThat(page.items()).extracting(item -> item.productId()).containsExactly(2L);
        assertThat(page.nextAfterProductId()).isNull();
    }

    @Test
    void anEmptyExportPageHasNoCursor() {
        when(productRepository.findAfterId(2L, PageRequest.of(0, 1))).thenReturn(List.of());

        InventoryExportDto page = service.export(2L, 1);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextAfterProductId()).isNull();
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
}
