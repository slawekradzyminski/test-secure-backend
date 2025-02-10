package com.awesome.testing.endpoints.cart;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.CartItemFactory.getDoubleCartItemFrom;
import static com.awesome.testing.factory.CartItemFactory.getSingleCartItemFrom;
import static com.awesome.testing.factory.ProductFactory.getRandomProduct;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class DeleteCartItemControllerTest extends AbstractCartTest {

    @Autowired
    private CartService cartService;

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldDeleteCartItem() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductEntity testProduct = getRandomProduct();
        ProductEntity testProduct2 = getRandomProduct();
        ProductEntity productEntity = productRepository.save(testProduct);
        ProductEntity productEntity2 = productRepository.save(testProduct2);
        CartItemDto cartItemDto = getSingleCartItemFrom(productEntity.getId());
        CartItemDto cartItemDto2 = getDoubleCartItemFrom(productEntity2.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);
        cartService.addToCart(client.getUsername(), cartItemDto2);

        // when
        ResponseEntity<CartDto> response = executeDelete(
                CART_ENDPOINT + "/items/" + productEntity2.getId(),
                getHeadersWith(clientToken),
                CartDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(client.getUsername());
        assertThat(response.getBody().getItems()).containsExactlyInAnyOrder(cartItemDto);
        assertThat(response.getBody().getTotalItems()).isEqualTo(1);
        assertThat(response.getBody().getTotalPrice()).isEqualTo(
                productEntity.getPrice()
        );
    }

    @Test
    public void shouldGet401AsUnauthorized() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        ProductEntity testProduct = getRandomProduct();
        ProductEntity productEntity = productRepository.save(testProduct);
        CartItemDto cartItemDto = getSingleCartItemFrom(productEntity.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);

        // when
        ResponseEntity<Object> response = executeDelete(
                CART_ENDPOINT + "/items/" + productEntity.getId(),
                getJsonOnlyHeaders(),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet404ForMissingCartItem() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductEntity testProduct = getRandomProduct();
        ProductEntity productEntity = productRepository.save(testProduct);

        // when
        ResponseEntity<ErrorDto> response = executeDelete(
                CART_ENDPOINT + "/items/" + productEntity.getId(),
                getHeadersWith(clientToken),
                ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Cart item not found");
    }
} 