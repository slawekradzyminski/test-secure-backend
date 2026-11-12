package com.awesome.testing.endpoints.products;

import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.dto.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

/** Product create and update field limits (catalog T-012, T-013, T-014). */
@SuppressWarnings("ConstantConditions")
class ProductFieldValidationTest extends AbstractEcommerceTest {

    private static final String LONG_URL = "https://example.com/" + "a".repeat(236);

    private String adminToken;

    @BeforeEach
    void admin() {
        adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));
    }

    @Test
    void createRejectsAnOverlongCategoryAndImageUrl() {
        // given
        Map<String, Object> body = validProduct();
        body.put("category", "c".repeat(256));
        body.put("imageUrl", LONG_URL);

        // when
        ResponseEntity<Map<String, String>> response = create(body);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "category", "Category must be at most 255 characters",
                "imageUrl", "Image URL must be at most 255 characters"));
    }

    @Test
    void createAcceptsCategoryAndImageUrlOf255Characters() {
        // given
        Map<String, Object> body = validProduct();
        body.put("category", "c".repeat(255));
        body.put("imageUrl", LONG_URL.substring(0, 255));

        // when
        ResponseEntity<Map<String, String>> response = create(body);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void updateRejectsAnOverlongCategoryAndImageUrl() {
        // given
        ProductEntity product = setupProduct();

        // when
        ResponseEntity<Map<String, String>> response = update(product,
                Map.of("category", "c".repeat(256), "imageUrl", LONG_URL));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "category", "Category must be at most 255 characters",
                "imageUrl", "Image URL must be at most 255 characters"));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getCategory())
                .isEqualTo(product.getCategory());
    }

    private ResponseEntity<Map<String, String>> create(Map<String, Object> body) {
        return executePost(PRODUCTS_ENDPOINT, body, getHeadersWith(adminToken), mapTypeReference());
    }

    private ResponseEntity<Map<String, String>> update(ProductEntity product, Map<String, Object> body) {
        return executePut(PRODUCTS_ENDPOINT + "/" + product.getId(), body, getHeadersWith(adminToken),
                mapTypeReference());
    }

    private static Map<String, Object> validProduct() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Validation probe");
        body.put("description", "A product used by validation tests");
        body.put("price", 10);
        body.put("stockQuantity", 1);
        body.put("category", "Probes");
        body.put("imageUrl", "https://example.com/probe.png");
        return body;
    }
}
