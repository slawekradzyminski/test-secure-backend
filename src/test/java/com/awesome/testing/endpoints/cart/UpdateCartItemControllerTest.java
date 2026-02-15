package com.awesome.testing.endpoints.cart;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.cart.UpdateCartItemDto;
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
import java.util.Map;

import static com.awesome.testing.factory.CartItemFactory.getDoubleCartItemFrom;
import static com.awesome.testing.factory.CartItemFactory.getSingleCartItemFrom;
import static com.awesome.testing.factory.ProductFactory.getRandomProduct;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

class UpdateCartItemControllerTest extends AbstractEcommerceTest {

    @Autowired
    private CartService cartService;

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldUpdateCartItem() {
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
        UpdateCartItemDto updateCartItem = UpdateCartItemDto.builder().quantity(2).build();
        CartItemDto expectedCartItemInResponse = getDoubleCartItemFrom(productEntity.getId());

        // when
        ResponseEntity<CartDto> response = executePut(
                CART_ENDPOINT + "/items/" + productEntity.getId(),
                updateCartItem,
                getHeadersWith(clientToken),
                CartDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(client.getUsername());
        assertThat(response.getBody().getItems()).containsExactlyInAnyOrder(expectedCartItemInResponse, cartItemDto2);
        assertThat(response.getBody().getTotalItems()).isEqualTo(4);
        assertThat(response.getBody().getTotalPrice()).isEqualTo(
                productEntity.getPrice()
                        .add(productEntity.getPrice())
                        .add(productEntity2.getPrice())
                        .add(productEntity2.getPrice())
        );
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldGet400ForNegativeQuantity() {
        // given
        ProductEntity testProduct = getRandomProduct();
        ProductEntity productEntity = productRepository.save(testProduct);
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        UpdateCartItemDto updateCartItem = UpdateCartItemDto.builder().quantity(-1).build();

        // when
        ResponseEntity<Map<String, String>> response = executePut(
                CART_ENDPOINT + "/items/" + productEntity.getId(),
                updateCartItem,
                getHeadersWith(clientToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("quantity")).isEqualTo("Quantity cannot be negative");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldRemoveItemWhenQuantitySetToZero() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductEntity productEntity = productRepository.save(getRandomProduct());
        CartItemDto cartItemDto = getSingleCartItemFrom(productEntity.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);
        UpdateCartItemDto updateCartItem = UpdateCartItemDto.builder().quantity(0).build();

        // when
        ResponseEntity<CartDto> response = executePut(
                CART_ENDPOINT + "/items/" + productEntity.getId(),
                updateCartItem,
                getHeadersWith(clientToken),
                CartDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getItems()).isEmpty();
        assertThat(response.getBody().getTotalItems()).isZero();
        assertThat(response.getBody().getTotalPrice()).isZero();
    }

    @Test
    void shouldGet401AsUnauthorized() {
        // given
        ProductEntity testProduct = getRandomProduct();
        ProductEntity productEntity = productRepository.save(testProduct);
        UpdateCartItemDto updateCartItemDto = UpdateCartItemDto.builder().quantity(1).build();

        // when
        ResponseEntity<Object> response = executePut(
                CART_ENDPOINT + "/items/" + productEntity.getId(),
                updateCartItemDto,
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
        ProductEntity testProduct = getRandomProduct();
        ProductEntity productEntity = productRepository.save(testProduct);
        UpdateCartItemDto updateCartItem = UpdateCartItemDto.builder().quantity(1).build();

        // when
        ResponseEntity<ErrorDto> response = executePut(
                CART_ENDPOINT + "/items/" + productEntity.getId(),
                updateCartItem,
                getHeadersWith(clientToken),
                ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Cart item not found");
    }
} 
