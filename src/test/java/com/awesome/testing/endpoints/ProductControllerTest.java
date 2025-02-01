package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ProductDTO;
import com.awesome.testing.dto.UserRegisterDTO;
import com.awesome.testing.model.Product;
import com.awesome.testing.model.Role;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.util.UserUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static com.awesome.testing.util.TypeReferenceUtil.productListTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

public class ProductControllerTest extends DomainHelper {

    private static final String PRODUCTS_ENDPOINT = "/api/products";

    @Autowired
    private ProductRepository productRepository;

    private String adminToken;
    private String clientToken;
    private Product testProduct;

    @BeforeEach
    public void setUp() {
        // given
        productRepository.deleteAll();

        UserRegisterDTO admin = UserUtil.getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        UserRegisterDTO client = UserUtil.getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        adminToken = getToken(admin);
        clientToken = getToken(client);

        testProduct = Product.builder()
                .name("Test Product")
                .description("Test Description")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(10)
                .category("Test Category")
                .build();
        testProduct = productRepository.save(testProduct);
    }

    @Test
    public void shouldGetAllProductsWhenAuthenticated() {
        // when
        ResponseEntity<List<Product>> response = executeGet(
                PRODUCTS_ENDPOINT,
                getHeadersWith(clientToken),
                productListTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getName()).isEqualTo(testProduct.getName());
    }

    @Test
    public void shouldFailToGetProductsWhenNotAuthenticated() {
        // when
        ResponseEntity<Map<String, String>> response = executeGet(
                PRODUCTS_ENDPOINT,
                getJsonOnlyHeaders(),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldGetProductById() {
        // when
        ResponseEntity<Product> response = executeGet(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                getHeadersWith(clientToken),
                Product.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(testProduct.getName());
    }

    @Test
    public void shouldCreateProductAsAdmin() {
        // given
        ProductDTO newProduct = ProductDTO.builder()
                .name("New Product")
                .description("New Description")
                .price(BigDecimal.valueOf(199.99))
                .stockQuantity(20)
                .category("New Category")
                .build();

        // when
        ResponseEntity<Product> response = executePost(
                PRODUCTS_ENDPOINT,
                newProduct,
                getHeadersWith(adminToken),
                Product.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(newProduct.getName());
    }

    @Test
    public void shouldFailToCreateProductAsClient() {
        // given
        ProductDTO newProduct = ProductDTO.builder()
                .name("New Product")
                .description("New Description")
                .price(BigDecimal.valueOf(199.99))
                .stockQuantity(20)
                .category("New Category")
                .build();

        // when
        ResponseEntity<Map<String, String>> response = executePost(
                PRODUCTS_ENDPOINT,
                newProduct,
                getHeadersWith(clientToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldUpdateProductAsAdmin() {
        // given
        ProductDTO updatedProduct = ProductDTO.builder()
                .name("Updated Product")
                .description("Updated Description")
                .price(BigDecimal.valueOf(299.99))
                .stockQuantity(30)
                .category("Updated Category")
                .build();

        // when
        ResponseEntity<Product> response = executePut(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                updatedProduct,
                getHeadersWith(adminToken),
                Product.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(updatedProduct.getName());
    }

    @Test
    public void shouldFailToUpdateProductAsClient() {
        // given
        ProductDTO updatedProduct = ProductDTO.builder()
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