package com.awesome.testing.endpoints.cart;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.CartItemFactory.getDoubleCartItemFrom;
import static com.awesome.testing.factory.CartItemFactory.getSingleCartItemFrom;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class DeleteCartItemControllerTest extends AbstractEcommerceTest {

    @Autowired
    private CartService cartService;

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldDeleteCartItem() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductEntity testProduct = setupProduct();
        ProductEntity testProduct2 = setupProduct();
        CartItemDto cartItemDto = getSingleCartItemFrom(testProduct.getId());
        CartItemDto cartItemDto2 = getDoubleCartItemFrom(testProduct2.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);
        cartService.addToCart(client.getUsername(), cartItemDto2);

        // when
        ResponseEntity<CartDto> response = executeDelete(
                CART_ENDPOINT + "/items/" + testProduct2.getId(),
                getHeadersWith(clientToken),
                CartDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(client.getUsername());
        assertThat(response.getBody().getItems()).containsExactlyInAnyOrder(cartItemDto);
        assertThat(response.getBody().getTotalItems()).isEqualTo(1);
        assertThat(response.getBody().getTotalPrice()).isEqualTo(
                testProduct.getPrice()
        );
    }

    @Test
    void shouldGet401AsUnauthorized() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        ProductEntity testProduct = setupProduct();
        CartItemDto cartItemDto = getSingleCartItemFrom(testProduct.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);

        // when
        ResponseEntity<Object> response = executeDelete(
                CART_ENDPOINT + "/items/" + testProduct.getId(),
                getJsonOnlyHeaders(),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldGet404ForMissingCartItem() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductEntity testProduct = setupProduct();

        // when
        ResponseEntity<ErrorDto> response = executeDelete(
                CART_ENDPOINT + "/items/" + testProduct.getId(),
                getHeadersWith(clientToken),
                ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Cart item not found");
    }
} 