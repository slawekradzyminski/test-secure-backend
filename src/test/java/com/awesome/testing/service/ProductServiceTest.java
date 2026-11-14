package com.awesome.testing.service;

import com.awesome.testing.controller.exception.ProductNotFoundException;
import com.awesome.testing.controller.exception.CustomException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.doThrow;
import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.dto.product.ProductSummaryDto;
import com.awesome.testing.dto.product.ProductUpdateDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import com.awesome.testing.service.stock.StockCommandOutbox;
import org.springframework.transaction.PlatformTransactionManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import com.awesome.testing.repository.pagination.OffsetPageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private StockCommandOutbox stockCommands;

    @Mock
    private com.awesome.testing.service.stock.StockCommandDispatcher stockCommandDispatcher;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private ProductService productService;

    private ProductEntity entity;

    @BeforeEach
    void setUp() {
        entity = ProductEntity.builder()
                .id(1L)
                .name("Laptop")
                .description("Gaming laptop")
                .price(BigDecimal.valueOf(1200))
                .stockQuantity(5)
                .category("Electronics")
                .imageUrl("https://example.com/laptop.png")
                .build();
    }

    @Test
    void shouldReturnAllProducts() {
        when(productRepository.findAll()).thenReturn(List.of(entity));

        List<ProductDto> result = productService.getAllProducts();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Laptop");
    }

    @Test
    void shouldGetProductById() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(entity));

        ProductDto dto = productService.getProductById(1L);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getDescription()).isEqualTo("Gaming laptop");
    }

    @Test
    void shouldThrowWhenProductNotFound() {
        when(productRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(2L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void shouldGetProductByName() {
        when(productRepository.findFirstByNameIgnoreCaseOrderByIdAsc("Laptop")).thenReturn(Optional.of(entity));

        ProductDto dto = productService.getProductByName("Laptop");

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Laptop");
    }

    @Test
    void shouldThrowWhenNameNotFound() {
        when(productRepository.findFirstByNameIgnoreCaseOrderByIdAsc("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductByName("Missing"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void shouldCreateProduct() {
        ProductCreateDto createDto = ProductCreateDto.builder()
                .name("Phone")
                .description("Smartphone")
                .price(BigDecimal.valueOf(900))
                .stockQuantity(10)
                .category("Electronics")
                .imageUrl("https://example.com/phone.png")
                .build();
        when(productRepository.save(any(ProductEntity.class)))
                .thenAnswer(inv -> {
                    ProductEntity saved = inv.getArgument(0);
                    saved.setId(2L);
                    return saved;
                });

        when(stockCommands.initialStock(any(ProductEntity.class), eq("admin")))
                .thenReturn(com.awesome.testing.entity.stock.StockCommandEntity.builder().id(9L).build());

        ProductDto result = productService.createProduct(createDto);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getName()).isEqualTo("Phone");
        verify(productRepository).save(any(ProductEntity.class));
        // The stock item is created in warehouse-service after commit (contract section 4.3).
        verify(stockCommands).initialStock(argThat(saved -> saved.getId() == 2L && saved.getStockQuantity() == 10),
                eq("admin"));
        // Sent before the response, after the product's transaction (contract section 4.3).
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(transactionManager, stockCommandDispatcher);
        order.verify(transactionManager).commit(any());
        order.verify(stockCommandDispatcher).sendNow(9L);
    }

    @Test
    void shouldUpdateProduct() {
        ProductUpdateDto updateDto = ProductUpdateDto.builder()
                .name("Updated laptop")
                .description("Updated description")
                .price(BigDecimal.valueOf(999))
                .stockQuantity(7)
                .category("Computers")
                .imageUrl("https://example.com/updated-laptop.png")
                .build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(entity));
        doAnswer(invocation -> {
            entity.setStockQuantity(entity.getStockQuantity()
                    + invocation.<com.awesome.testing.dto.inventory.InventoryAdjustmentDto>getArgument(1).getDelta());
            return null;
        }).when(inventoryService).adjust(eq(1L), any(), eq("admin"));
        when(productRepository.saveAndFlush(entity)).thenReturn(entity);

        ProductDto updated = productService.updateProduct(1L, updateDto);

        assertThat(updated.getName()).isEqualTo("Updated laptop");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getPrice()).isEqualTo(BigDecimal.valueOf(999));
        assertThat(updated.getStockQuantity()).isEqualTo(7);
        assertThat(updated.getCategory()).isEqualTo("Computers");
        assertThat(updated.getImageUrl()).isEqualTo("https://example.com/updated-laptop.png");
        verify(productRepository).saveAndFlush(entity);
        verify(inventoryService).adjust(eq(1L), argThat(adjustment -> adjustment.getDelta() == 2
                && adjustment.getReason().equals("Product update") && adjustment.getRequestId() != null), eq("admin"));
    }

    @Test
    void aRejectedStockAdjustmentLeavesTheOtherFieldsUnchanged() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(entity));
        doThrow(new CustomException("Inventory service unavailable", HttpStatus.SERVICE_UNAVAILABLE))
                .when(inventoryService).adjust(eq(1L), any(), eq("admin"));

        assertThatThrownBy(() -> productService.updateProduct(1L,
                ProductUpdateDto.builder().name("Renamed").stockQuantity(9).build()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(entity.getName()).isEqualTo("Laptop");
        verify(productRepository, never()).saveAndFlush(any(ProductEntity.class));
    }

    @Test
    void anUnchangedStockQuantityIsNoAdjustmentAndKeepsTheIfMatchCheckOnTheWrite() {
        entity.setVersion(3L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
        when(productRepository.saveAndFlush(entity)).thenReturn(entity);

        var updated = productService.updateProduct(1L, ProductUpdateDto.builder().name("Renamed").stockQuantity(5).build(), 3L);

        assertThat(updated.product().getName()).isEqualTo("Renamed");
        verify(inventoryService, never()).adjust(any(), any(), any());
    }

    @Test
    void aStaleIfMatchFailsBeforeTheStockIsAdjusted() {
        entity.setVersion(4L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> productService.updateProduct(1L, ProductUpdateDto.builder().stockQuantity(9).build(), 3L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.PRECONDITION_FAILED));

        verify(inventoryService, never()).adjust(any(), any(), any());
    }

    @Test
    void anIfMatchUpdateWithAStockChangeChecksTheVersionOnceBeforeTheAdjustment() {
        entity.setVersion(3L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(entity));
        doAnswer(invocation -> {
            // The projection update bumps the version; the write must not fail on it.
            entity.setVersion(4L);
            entity.setStockQuantity(9);
            return null;
        }).when(inventoryService).adjust(eq(1L), any(), eq("admin"));
        when(productRepository.saveAndFlush(entity)).thenReturn(entity);

        var updated = productService.updateProduct(1L, ProductUpdateDto.builder().stockQuantity(9).build(), 3L);

        assertThat(updated.product().getStockQuantity()).isEqualTo(9);
        assertThat(updated.version()).isEqualTo(4L);
        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void shouldThrowWhenUpdatingMissingProductWithoutSaving() {
        when(productRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(2L, ProductUpdateDto.builder().build()))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found");

        verify(productRepository, never()).saveAndFlush(any(ProductEntity.class));
    }

    @Test
    void shouldRejectStockUpdateWhenProductCannotBeLocked() {
        ProductUpdateDto updateDto = ProductUpdateDto.builder().stockQuantity(7).build();
        when(productRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(2L, updateDto))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found");

        verify(inventoryService, never()).adjust(any(), any(), any());
        verify(productRepository, never()).saveAndFlush(any(ProductEntity.class));
    }

    @Test
    void shouldDeleteExistingProduct() {
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));

        boolean deleted = productService.deleteProduct(1L);

        assertThat(deleted).isTrue();
        verify(productRepository).delete(entity);
        verify(stockCommands).delete(1L);
    }

    @Test
    void referencedProductFailureIsMappedBeforeTransactionCommit() {
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
        doThrow(new DataIntegrityViolationException("private constraint"))
                .when(productRepository).flush();
        assertThatThrownBy(() -> productService.deleteProduct(1L))
                .isInstanceOfSatisfying(CustomException.class, error -> {
                    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getMessage()).isEqualTo("Product is referenced by existing data");
                });
        verify(stockCommands, never()).delete(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldReturnFalseWhenDeletingMissingProduct() {
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

        boolean deleted = productService.deleteProduct(5L);

        assertThat(deleted).isFalse();
    }

    @Test
    void shouldListProductsWithOffsetLimit() {
        List<ProductEntity> products = java.util.stream.LongStream.rangeClosed(3, 5)
                .mapToObj(id -> ProductEntity.builder()
                        .id(id)
                        .name("Product " + id)
                        .price(BigDecimal.TEN)
                        .stockQuantity(1)
                        .build())
                .toList();
        when(productRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<ProductEntity>>any(),
                eq(new OffsetPageRequest(2, 3))))
                .thenReturn(new PageImpl<>(products, new OffsetPageRequest(2, 3), 5));

        var result = productService.listProducts(2, 3, null, null);

        assertThat(result.getProducts())
                .extracting(ProductSummaryDto::getId)
                .containsExactly(3L, 4L, 5L);
        assertThat(result.getTotal()).isEqualTo(5);
        verify(productRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<ProductEntity>>any(),
                eq(new OffsetPageRequest(2, 3)));
    }
}
