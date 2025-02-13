package com.awesome.testing.endpoints.cart;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.CartItemFactory.getDoubleCartItemFrom;
import static com.awesome.testing.factory.CartItemFactory.getSingleCartItemFrom;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

public class AddToCartControllerTest extends AbstractEcommerceTest {

    @Autowired
    private CartService cartService;

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldAddToCart() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductEntity testProduct = setupProduct();
        ProductEntity testProduct2 = setupProduct();
        CartItemDto cartItemDto = getSingleCartItemFrom(testProduct.getId());
        CartItemDto cartItemDto2 = getDoubleCartItemFrom(testProduct2.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);

        // when
        ResponseEntity<CartDto> response = executePost(
                CART_ENDPOINT + "/items",
                cartItemDto2,
                getHeadersWith(clientToken),
                CartDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(client.getUsername());
        assertThat(response.getBody().getItems()).containsExactlyInAnyOrder(cartItemDto, cartItemDto2);
        assertThat(response.getBody().getTotalItems()).isEqualTo(3);
        assertThat(response.getBody().getTotalPrice()).isEqualTo(
                testProduct.getPrice()
                        .add(testProduct2.getPrice())
                        .add(testProduct2.getPrice())
        );
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet400ForInvalidRequestBody() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        CartItemDto cartItemDto = CartItemDto.builder().quantity(1).build();

        // when
        ResponseEntity<Map<String, String>> response = executePost(
                CART_ENDPOINT + "/items",
                cartItemDto,
                getHeadersWith(clientToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("productId")).isEqualTo("must not be null");
    }

    @Test
    public void shouldGet401AsUnauthorized() {
        // given
        ProductEntity testProduct = setupProduct();
        CartItemDto cartItemDto = getSingleCartItemFrom(testProduct.getId());

        // when
        ResponseEntity<CartDto> response = executePost(
                CART_ENDPOINT + "/items",
                cartItemDto,
                getJsonOnlyHeaders(),
                CartDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet404ForMissingProduct() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        CartItemDto cartItemDto = CartItemDto.builder().productId(6666L).quantity(1).build();

        // when
        ResponseEntity<ErrorDto> response = executePost(
                CART_ENDPOINT + "/items",
                cartItemDto,
                getHeadersWith(clientToken),
                ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Product not found");
    }
} 