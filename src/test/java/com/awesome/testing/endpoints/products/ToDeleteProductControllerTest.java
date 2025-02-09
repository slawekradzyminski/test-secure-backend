package com.awesome.testing.endpoints.products;

import com.awesome.testing.dto.ProductDto;
import com.awesome.testing.dto.UserRegisterDto;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.model.Role;
import com.awesome.testing.factory.UserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

public class ToDeleteProductControllerTest extends AbstractProductTest {

    private static final String PRODUCTS_ENDPOINT = "/api/products";

    private String adminToken;
    private String clientToken;
    private ProductEntity testProduct;

    @BeforeEach
    @Transactional
    public void setUp() {
        // Clean up related data first
        productRepository.deleteAll();

        UserRegisterDto admin = UserFactory.getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        UserRegisterDto client = UserFactory.getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        adminToken = getToken(admin);
        clientToken = getToken(client);

        testProduct = ProductEntity.builder()
                .name("Test Product")
                .description("Test Description")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(10)
                .category("Test Category")
                .build();
        testProduct = productRepository.save(testProduct);
    }

    @Test
    public void shouldUpdateProductAsAdmin() {
        // given
        ProductDto updatedProduct = ProductDto.builder()
                .name("Updated Product")
                .description("Updated Description")
                .price(BigDecimal.valueOf(299.99))
                .stockQuantity(30)
                .category("Updated Category")
                .build();

        // when
        ResponseEntity<ProductEntity> response = executePut(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                updatedProduct,
                getHeadersWith(adminToken),
                ProductEntity.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(updatedProduct.getName());
    }

    @Test
    public void shouldFailToUpdateProductAsClient() {
        // given
        ProductDto updatedProduct = ProductDto.builder()
                .name("Updated Product")
                .description("Updated Description")
                .price(BigDecimal.valueOf(299.99))
                .stockQuantity(30)
                .category("Updated Category")
                .build();

        // when
        ResponseEntity<Map<String, String>> response = executePut(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                updatedProduct,
                getHeadersWith(clientToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldDeleteProductAsAdmin() {
        // when
        ResponseEntity<Void> response = executeDelete(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                getHeadersWith(adminToken),
                Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    public void shouldFailToDeleteProductAsClient() {
        // when
        ResponseEntity<Map<String, String>> response = executeDelete(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                getHeadersWith(clientToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
} 