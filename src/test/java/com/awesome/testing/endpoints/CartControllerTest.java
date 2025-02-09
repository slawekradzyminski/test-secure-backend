package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.CartDTO;
import com.awesome.testing.dto.CartItemDTO;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.factory.UserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CartControllerTest extends DomainHelper {

    private static final String CART_ENDPOINT = "/api/cart";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    private String clientToken;
    private ProductEntity testProduct;

    @BeforeEach
    public void setUp() {
        // given
        cartItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();

        UserRegisterDto client = UserFactory.getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
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
    public void shouldGetEmptyCart() {
        // when
        ResponseEntity<CartDTO> response = executeGet(
                CART_ENDPOINT,
                getHeadersWith(clientToken),
                CartDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getItems()).isEmpty();
        assertThat(response.getBody().getTotalItems()).isZero();
        assertThat(response.getBody().getTotalPrice()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    public void shouldAddItemToCart() {
        // given
        CartItemDTO cartItemDTO = CartItemDTO.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        // when
        ResponseEntity<CartDTO> response = executePost(
                CART_ENDPOINT + "/items",
                cartItemDTO,
                getHeadersWith(clientToken),
                CartDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getItems()).hasSize(1);
        assertThat(response.getBody().getTotalItems()).isEqualTo(2);
        assertThat(response.getBody().getTotalPrice())
                .isEqualTo(testProduct.getPrice().multiply(BigDecimal.valueOf(2)));
    }

    @Test
    public void shouldUpdateCartItemQuantity() {
        // given
        CartItemDTO cartItemDTO = CartItemDTO.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();
        executePost(CART_ENDPOINT + "/items", cartItemDTO, getHeadersWith(clientToken), CartDTO.class);

        CartItemDTO updateDTO = CartItemDTO.builder()
                .productId(testProduct.getId())
                .quantity(3)
                .build();

        // when
        ResponseEntity<CartDTO> response = executePut(
                CART_ENDPOINT + "/items/" + testProduct.getId(),
                updateDTO,
                getHeadersWith(clientToken),
                CartDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getItems()).hasSize(1);
        assertThat(response.getBody().getTotalItems()).isEqualTo(3);
        assertThat(response.getBody().getTotalPrice())
                .isEqualTo(testProduct.getPrice().multiply(BigDecimal.valueOf(3)));
    }

    @Test
    public void shouldRemoveItemFromCart() {
        // given
        CartItemDTO cartItemDTO = CartItemDTO.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();
        executePost(CART_ENDPOINT + "/items", cartItemDTO, getHeadersWith(clientToken), CartDTO.class);

        // when
        ResponseEntity<CartDTO> response = executeDelete(
                CART_ENDPOINT + "/items/" + testProduct.getId(),
                getHeadersWith(clientToken),
                CartDTO.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getItems()).isEmpty();
        assertThat(response.getBody().getTotalItems()).isZero();
        assertThat(response.getBody().getTotalPrice()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    public void shouldClearCart() {
        // given
        CartItemDTO cartItemDTO = CartItemDTO.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();
        executePost(CART_ENDPOINT + "/items", cartItemDTO, getHeadersWith(clientToken), CartDTO.class);

        // when
        ResponseEntity<Void> response = executeDelete(
                CART_ENDPOINT,
                getHeadersWith(clientToken),
                Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<CartDTO> cartResponse = executeGet(
                CART_ENDPOINT,
                getHeadersWith(clientToken),
                CartDTO.class);
        assertThat(cartResponse.getBody()).isNotNull();
        assertThat(cartResponse.getBody().getItems()).isEmpty();
    }

    @Test
    public void shouldFailToGetCartWhenNotAuthenticated() {
        // when
        ResponseEntity<ErrorDto> response = executeGet(
                CART_ENDPOINT,
                getJsonOnlyHeaders(),
                ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
} 