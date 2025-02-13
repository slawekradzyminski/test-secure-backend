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

public class GetCartControllerTest extends AbstractEcommerceTest {

    @Autowired
    private CartService cartService;

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGetCart() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductEntity productEntity = setupProduct();
        ProductEntity productEntity2 = setupProduct();
        CartItemDto cartItemDto = getSingleCartItemFrom(productEntity.getId());
        CartItemDto cartItemDto2 = getDoubleCartItemFrom(productEntity2.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);
        cartService.addToCart(client.getUsername(), cartItemDto2);

        // when
        ResponseEntity<CartDto> response = executeGet(
                CART_ENDPOINT,
                getHeadersWith(clientToken),
                CartDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(client.getUsername());
        assertThat(response.getBody().getItems()).containsExactlyInAnyOrder(cartItemDto, cartItemDto2);
        assertThat(response.getBody().getTotalItems()).isEqualTo(3);
        assertThat(response.getBody().getTotalPrice()).isEqualTo(
                productEntity.getPrice()
                        .add(productEntity2.getPrice())
                        .add(productEntity2.getPrice())
        );
    }

    @Test
    public void shouldGet401AsUnauthorized() {
        // when
        ResponseEntity<ErrorDto> response = executeGet(
                CART_ENDPOINT,
                getJsonOnlyHeaders(),
                ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
} 