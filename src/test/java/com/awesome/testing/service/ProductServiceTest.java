package com.awesome.testing.service;

import com.awesome.testing.controller.exception.ProductNotFoundException;
import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.dto.product.ProductUpdateDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

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

        ProductDto result = productService.createProduct(createDto);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getName()).isEqualTo("Phone");
        verify(productRepository).save(any(ProductEntity.class));
    }

    @Test
    void shouldUpdateProduct() {
        ProductUpdateDto updateDto = ProductUpdateDto.builder()
                .description("Updated description")
                .price(BigDecimal.valueOf(999))
                .stockQuantity(7)
                .build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(productRepository.saveAndFlush(entity)).thenReturn(entity);

        ProductDto updated = productService.updateProduct(1L, updateDto);

        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getPrice()).isEqualTo(BigDecimal.valueOf(999));
        assertThat(updated.getStockQuantity()).isEqualTo(7);
        verify(productRepository).saveAndFlush(entity);
    }

    @Test
    void shouldDeleteExistingProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(entity));

        boolean deleted = productService.deleteProduct(1L);

        assertThat(deleted).isTrue();
    }

    @Test
    void shouldReturnFalseWhenDeletingMissingProduct() {
        when(productRepository.findById(5L)).thenReturn(Optional.empty());

        boolean deleted = productService.deleteProduct(5L);

        assertThat(deleted).isFalse();
    }

    @Test
    void shouldListProductsWithPaging() {
        when(productRepository.findAll(PageRequest.of(0, 25)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 25), 1));

        var result = productService.listProducts(0, 25);

        assertThat(result.getProducts()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
        verify(productRepository).findAll(PageRequest.of(0, 25));
    }
}
