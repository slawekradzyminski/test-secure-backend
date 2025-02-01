package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.*;
import com.awesome.testing.model.OrderStatus;
import com.awesome.testing.model.Product;
import com.awesome.testing.model.Role;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.util.UserUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderControllerTest extends DomainHelper {

    private static final String ORDERS_ENDPOINT = "/api/orders";
    private static final String CART_ITEMS_ENDPOINT = "/api/cart/items";

    @LocalServerPort
    private int port;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Product testProduct;
    private AddressDTO testAddress;
    private String clientToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();

        UserRegisterDTO client = UserUtil.getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        UserRegisterDTO admin = UserUtil.getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        clientToken = getToken(client);
        adminToken = getToken(admin);

        testProduct = Product.builder()
                .name("Test Product")
                .description("Test Description")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(10)
                .category("Test Category")
                .build();
        testProduct = productRepository.save(testProduct);

        testAddress = AddressDTO.builder()
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
        CartItemDTO cartItemDTO = CartItemDTO.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders headers = getHeadersWith(clientToken);
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, headers, CartDTO.class);
        executePost(ORDERS_ENDPOINT, testAddress, headers, OrderDTO.class);

        // when
        ResponseEntity<PageDTO<OrderDTO>> response = executeGet(
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
        ResponseEntity<OrderDTO> response = executePost(
                ORDERS_ENDPOINT,
                testAddress,
                getHeadersWith(clientToken),
                OrderDTO.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldGetOrderById() {
        // given
        CartItemDTO cartItemDTO = CartItemDTO.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders headers = getHeadersWith(clientToken);
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, headers, CartDTO.class);
        ResponseEntity<OrderDTO> orderResponse = executePost(ORDERS_ENDPOINT, testAddress, headers, OrderDTO.class);
        assertThat(orderResponse.getBody()).isNotNull();
        Long orderId = orderResponse.getBody().getId();

        // when
        ResponseEntity<OrderDTO> response = executeGet(
                ORDERS_ENDPOINT + "/" + orderId,
                headers,
                OrderDTO.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(orderId);
    }

    @Test
    void shouldUpdateOrderStatusAsAdmin() {
        // given
        CartItemDTO cartItemDTO = CartItemDTO.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders clientHeaders = getHeadersWith(clientToken);
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, clientHeaders, CartDTO.class);
        ResponseEntity<OrderDTO> orderResponse = executePost(ORDERS_ENDPOINT, testAddress, clientHeaders, OrderDTO.class);
        assertThat(orderResponse.getBody()).isNotNull();
        Long orderId = orderResponse.getBody().getId();

        // when
        ResponseEntity<OrderDTO> response = executePut(
                ORDERS_ENDPOINT + "/" + orderId + "/status",
                OrderStatus.PAID,
                getHeadersWith(adminToken),
                OrderDTO.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void shouldFailToUpdateOrderStatusAsClient() {
        // given
        CartItemDTO cartItemDTO = CartItemDTO.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders clientHeaders = getHeadersWith(clientToken);
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, clientHeaders, CartDTO.class);
        ResponseEntity<OrderDTO> orderResponse = executePost(ORDERS_ENDPOINT, testAddress, clientHeaders, OrderDTO.class);
        assertThat(orderResponse.getBody()).isNotNull();
        Long orderId = orderResponse.getBody().getId();

        // when
        ResponseEntity<OrderDTO> response = executePut(
                ORDERS_ENDPOINT + "/" + orderId + "/status",
                OrderStatus.PAID,
                clientHeaders,
                OrderDTO.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldCancelOrder() {
        // given
        CartItemDTO cartItemDTO = CartItemDTO.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders clientHeaders = getHeadersWith(clientToken);
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, clientHeaders, CartDTO.class);
        ResponseEntity<OrderDTO> orderResponse = executePost(ORDERS_ENDPOINT, testAddress, clientHeaders, OrderDTO.class);
        assertThat(orderResponse.getBody()).isNotNull();
        Long orderId = orderResponse.getBody().getId();

        // when
        ResponseEntity<OrderDTO> response = executePost(
                ORDERS_ENDPOINT + "/" + orderId + "/cancel",
                null,
                clientHeaders,
                OrderDTO.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldFailToCancelDeliveredOrder() {
        // given
        CartItemDTO cartItemDTO = CartItemDTO.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        HttpHeaders clientHeaders = getHeadersWith(clientToken);
        HttpHeaders adminHeaders = getHeadersWith(adminToken);
        
        executePost(CART_ITEMS_ENDPOINT, cartItemDTO, clientHeaders, CartDTO.class);
        ResponseEntity<OrderDTO> orderResponse = executePost(ORDERS_ENDPOINT, testAddress, clientHeaders, OrderDTO.class);
        assertThat(orderResponse.getBody()).isNotNull();
        Long orderId = orderResponse.getBody().getId();

        executePut(ORDERS_ENDPOINT + "/" + orderId + "/status", OrderStatus.DELIVERED, adminHeaders, OrderDTO.class);

        // when
        ResponseEntity<OrderDTO> response = executePost(
                ORDERS_ENDPOINT + "/" + orderId + "/cancel",
                null,
                clientHeaders,
                OrderDTO.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
} 