package com.awesome.testing.endpoints.products;

import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.dto.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.ProductFactory.getRandomProduct;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

public class DeleteProductControllerTest extends AbstractEcommerceTest {

    private static final String PRODUCTS_ENDPOINT = "/api/products";

    @Test
    public void shouldDeleteProductAsAdmin() {
        // given
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);

        // when
        ResponseEntity<Void> response = executeDelete(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                getHeadersWith(adminToken),
                Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    public void shouldGet401AsUnauthorized() {
        // given
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);

        // when
        ResponseEntity<Object> response = executeDelete(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                getJsonOnlyHeaders(),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldGet403AsClient() {
        // given
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);

        // when
        ResponseEntity<Map<String, String>> response = executeDelete(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                getHeadersWith(clientToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet404ForMissingProduct() {
        // given
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);

        // when
        ResponseEntity<Void> response = executeDelete(
                PRODUCTS_ENDPOINT + "/666666",
                getHeadersWith(adminToken),
                Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
} 