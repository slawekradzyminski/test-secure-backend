package com.awesome.testing.endpoints.order;

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

import static com.awesome.testing.factory.CartItemFactory.getSingleCartItemFrom;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

/** Overlong checkout address fields (catalog T-015). */
@SuppressWarnings("ConstantConditions")
class CheckoutAddressValidationTest extends AbstractEcommerceTest {

    private static final String TOO_LONG = "x".repeat(256);

    @Autowired
    private CartService cartService;

    @Test
    void overlongAddressFieldsAreFieldErrorsAndLeaveTheCartAndStockUnchanged() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(client);
        ProductEntity product = setupProduct();
        cartService.addToCart(client.getUsername(), getSingleCartItemFrom(product.getId()));
        int stockBefore = productRepository.findById(product.getId()).orElseThrow().getStockQuantity();

        // when
        ResponseEntity<Map<String, String>> response = executePost(ORDERS_ENDPOINT,
                Map.of("street", TOO_LONG, "city", TOO_LONG, "state", TOO_LONG, "zipCode", "30-001",
                        "country", TOO_LONG),
                getHeadersWith(token), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "street", "Street must be at most 255 characters",
                "city", "City must be at most 255 characters",
                "state", "State must be at most 255 characters",
                "country", "Country must be at most 255 characters"));
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(cartItemRepository.findAll()).hasSize(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(stockBefore);
    }

    @Test
    void addressFieldsOf255CharactersAreAccepted() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(client);
        ProductEntity product = setupProduct();
        cartService.addToCart(client.getUsername(), getSingleCartItemFrom(product.getId()));
        String max = "x".repeat(255);

        // when
        ResponseEntity<String> response = executePost(ORDERS_ENDPOINT,
                Map.of("street", max, "city", max, "state", max, "zipCode", "30-001", "country", max),
                getHeadersWith(token), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
