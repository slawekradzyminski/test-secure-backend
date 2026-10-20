package com.awesome.testing.endpoints.products;

import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.service.CartService;
import com.awesome.testing.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
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

class DeleteProductControllerTest extends AbstractEcommerceTest {

    private static final String PRODUCTS_ENDPOINT = "/api/v1/products";

    @Autowired
    private CartService cartService;
    @Autowired
    private OrderService orderService;

    @Test
    void shouldDeleteProductAsAdmin() {
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
    void productReferencedByCartReturnsConflictAndCanBeDeletedAfterClearingCart() {
        // given
        var product = setupProduct();
        var customer = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        getToken(customer);
        var adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));
        cartService.addToCart(customer.getUsername(), new CartItemDto(product.getId(), 1));

        // when
        var response = executeDelete(PRODUCTS_ENDPOINT + "/" + product.getId(),
                getHeadersWith(adminToken), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Product is referenced by existing data");
        assertThat(productRepository.findById(product.getId())).isPresent();
        assertThat(cartService.getCart(customer.getUsername()).getTotalItems()).isEqualTo(1);

        // when
        cartService.clearCart(customer.getUsername());
        var deleted = executeDelete(PRODUCTS_ENDPOINT + "/" + product.getId(),
                getHeadersWith(adminToken), Void.class);

        // then
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void productReferencedByOrderReturnsConflictAndPreservesOrderAndStock() {
        // given
        var product = setupProduct();
        var customer = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        getToken(customer);
        var adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));
        cartService.addToCart(customer.getUsername(), new CartItemDto(product.getId(), 1));
        orderService.createOrder(customer.getUsername(), new AddressDto(
                "Main 1", "Warsaw", "Mazovia", "00-001", "Poland"));

        // when
        var response = executeDelete(PRODUCTS_ENDPOINT + "/" + product.getId(),
                getHeadersWith(adminToken), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Product is referenced by existing data");
        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(productRepository.findById(product.getId())).hasValueSatisfying(saved ->
                assertThat(saved.getStockQuantity()).isEqualTo(product.getStockQuantity() - 1));
    }

    @Test
    void shouldGet401AsUnauthorized() {
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
    void shouldGet403AsClient() {
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
    void shouldGet404ForMissingProduct() {
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