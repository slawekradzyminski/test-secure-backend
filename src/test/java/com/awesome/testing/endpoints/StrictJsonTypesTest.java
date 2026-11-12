package com.awesome.testing.endpoints;

import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.ProductEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

/** Wrongly typed JSON values are rejected instead of being coerced (catalog T-010). */
@SuppressWarnings("ConstantConditions")
class StrictJsonTypesTest extends AbstractEcommerceTest {

    private String clientToken;
    private String adminToken;

    @BeforeEach
    void tokens() {
        clientToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));
    }

    @Test
    void aFractionalCartQuantityIsRejectedAndNothingIsStored() {
        // given
        ProductEntity product = setupProduct();

        // when
        ResponseEntity<Map<String, String>> response = executePost(CART_ITEMS_ENDPOINT,
                "{\"productId\":" + product.getId() + ",\"quantity\":1.5}", getHeadersWith(clientToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("quantity", "Must be a whole number"));
        assertThat(cartItemRepository.findAll()).isEmpty();
    }

    @Test
    void aNumericStringQuantityIsRejected() {
        // given
        ProductEntity product = setupProduct();

        // when
        ResponseEntity<Map<String, String>> response = executePost(CART_ITEMS_ENDPOINT,
                "{\"productId\":" + product.getId() + ",\"quantity\":\"10\"}", getHeadersWith(clientToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("quantity", "Must be a whole number"));
    }

    @Test
    void aFractionalStockQuantityIsRejectedAndTheProductIsUnchanged() {
        // given
        ProductEntity product = setupProduct();

        // when
        ResponseEntity<Map<String, String>> response = executePut(PRODUCTS_ENDPOINT + "/" + product.getId(),
                "{\"stockQuantity\":1.9}", getHeadersWith(adminToken), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("stockQuantity", "Must be a whole number"));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(product.getStockQuantity());
    }

    @Test
    void aDecimalAmountMayStillBeSentAsAString() {
        // given
        ProductEntity product = setupProduct();

        // when
        ResponseEntity<String> response = executePut(PRODUCTS_ENDPOINT + "/" + product.getId(),
                "{\"price\":\"12.50\"}", getHeadersWith(adminToken), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("12.5");
    }

    @Test
    void booleanAndNumberNamesAreRejectedAtSignup() {
        // when
        ResponseEntity<Map<String, String>> response = executePost(REGISTER_ENDPOINT,
                "{\"username\":\"typedname\",\"email\":\"typed@example.com\",\"password\":\"TestPass123!\","
                        + "\"firstName\":true,\"lastName\":1234}",
                getJsonOnlyHeaders(), mapTypeReference());

        // then: Jackson stops at the first wrong field
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("firstName", "Must be a string"));
    }

    @Test
    void correctlyTypedValuesStillWork() {
        // given
        ProductEntity product = setupProduct();

        // when
        ResponseEntity<String> cart = executePost(CART_ITEMS_ENDPOINT,
                "{\"productId\":" + product.getId() + ",\"quantity\":2}", getHeadersWith(clientToken), String.class);
        ResponseEntity<String> update = executePut(PRODUCTS_ENDPOINT + "/" + product.getId(),
                "{\"price\":12.5,\"stockQuantity\":7}", getHeadersWith(adminToken), String.class);

        // then
        assertThat(cart.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(update.getBody()).contains("\"stockQuantity\":7");
    }
}
