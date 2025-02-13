package com.awesome.testing.endpoints.order;

import com.awesome.testing.dto.*;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.model.OrderStatus;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.factory.UserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OldOrderControllerTest extends AbstractEcommerceTest {


    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private ProductEntity testProduct;
    private AddressDto testAddress;
    private String clientToken;
    private String adminToken;

    @BeforeEach
    void setUpzz() {
        cartItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();

        UserRegisterDto client = UserFactory.getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        UserRegisterDto admin = UserFactory.getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        clientToken = getToken(client);
        adminToken = getToken(admin);

        testProduct = ProductEntity.builder()
                .name("Test Product")
                .description("Test Description")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(10)
                .category("Test Category")
                .build();
        testProduct = productRepository.save(testProduct);

        testAddress = AddressDto.builder()
                .street("123 Test St")
                .city("Test City")
                .state("TS")
                .zipCode("12345")
                .country("Test Country")
                .build();
    }

    @Test
    void shouldGetUserOrders() {
        // given
        CartItemDto cartItemDTO = CartItemDto.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders headers = getHeadersWith(clientToken);
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, headers, CartDto.class);
        executePost(ORDERS_ENDPOINT, testAddress, headers, OrderDto.class);

        // when
        ResponseEntity<PageDTO<OrderDto>> response = executeGet(
                ORDERS_ENDPOINT,
                headers,
                new ParameterizedTypeReference<>() {}
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldFailToCreateOrderWithEmptyCart() {
        // when
        ResponseEntity<OrderDto> response = executePost(
                ORDERS_ENDPOINT,
                testAddress,
                getHeadersWith(clientToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldGetOrderById() {
        // given
        CartItemDto cartItemDTO = CartItemDto.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders headers = getHeadersWith(clientToken);
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, headers, CartDto.class);
        ResponseEntity<OrderDto> orderResponse = executePost(ORDERS_ENDPOINT, testAddress, headers, OrderDto.class);
        assertThat(orderResponse.getBody()).isNotNull();
        Long orderId = orderResponse.getBody().getId();

        // when
        ResponseEntity<OrderDto> response = executeGet(
                ORDERS_ENDPOINT + "/" + orderId,
                headers,
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(orderId);
    }

    @Test
    void shouldUpdateOrderStatusAsAdmin() {
        // given
        CartItemDto cartItemDTO = CartItemDto.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders clientHeaders = getHeadersWith(clientToken);
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, clientHeaders, CartDto.class);
        ResponseEntity<OrderDto> orderResponse = executePost(ORDERS_ENDPOINT, testAddress, clientHeaders, OrderDto.class);
        assertThat(orderResponse.getBody()).isNotNull();
        Long orderId = orderResponse.getBody().getId();

        // when
        ResponseEntity<OrderDto> response = executePut(
                ORDERS_ENDPOINT + "/" + orderId + "/status",
                OrderStatus.PAID,
                getHeadersWith(adminToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void shouldFailToUpdateOrderStatusAsClient() {
        // given
        CartItemDto cartItemDTO = CartItemDto.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders clientHeaders = getHeadersWith(clientToken);
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, clientHeaders, CartDto.class);
        ResponseEntity<OrderDto> orderResponse = executePost(ORDERS_ENDPOINT, testAddress, clientHeaders, OrderDto.class);
        assertThat(orderResponse.getBody()).isNotNull();
        Long orderId = orderResponse.getBody().getId();

        // when
        ResponseEntity<OrderDto> response = executePut(
                ORDERS_ENDPOINT + "/" + orderId + "/status",
                OrderStatus.PAID,
                clientHeaders,
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldCancelOrder() {
        // given
        CartItemDto cartItemDTO = CartItemDto.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders clientHeaders = getHeadersWith(clientToken);
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, clientHeaders, CartDto.class);
        ResponseEntity<OrderDto> orderResponse = executePost(ORDERS_ENDPOINT, testAddress, clientHeaders, OrderDto.class);
        assertThat(orderResponse.getBody()).isNotNull();
        Long orderId = orderResponse.getBody().getId();

        // when
        ResponseEntity<OrderDto> response = executePost(
                ORDERS_ENDPOINT + "/" + orderId + "/cancel",
                null,
                clientHeaders,
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldFailToCancelDeliveredOrder() {
        // given
        CartItemDto cartItemDTO = CartItemDto.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders clientHeaders = getHeadersWith(clientToken);
        HttpHeaders adminHeaders = getHeadersWith(adminToken);
        
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, clientHeaders, CartDto.class);
        ResponseEntity<OrderDto> orderResponse = executePost(ORDERS_ENDPOINT, testAddress, clientHeaders, OrderDto.class);
        assertThat(orderResponse.getBody()).isNotNull();
        Long orderId = orderResponse.getBody().getId();

        executePut(ORDERS_ENDPOINT + "/" + orderId + "/status", OrderStatus.DELIVERED, adminHeaders, OrderDto.class);

        // when
        ResponseEntity<OrderDto> response = executePost(
                ORDERS_ENDPOINT + "/" + orderId + "/cancel",
                null,
                clientHeaders,
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
} 