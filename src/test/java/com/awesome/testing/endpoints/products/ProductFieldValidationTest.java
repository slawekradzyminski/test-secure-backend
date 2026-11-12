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

    @Test
    void updateRejectsAnImageUrlWithoutAValidHost() {
        // given
        ProductEntity product = setupProduct();

        // when
        ResponseEntity<Map<String, String>> response = update(product, Map.of("imageUrl", "https://not a host"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("imageUrl", "Image URL must be a valid URL or empty"));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getImageUrl())
                .isEqualTo(product.getImageUrl());
    }

    @Test
    void createRejectsAnImageUrlWithoutAHost() {
        // given
        Map<String, Object> body = validProduct();
        body.put("imageUrl", "https://");

        // when
        ResponseEntity<Map<String, String>> response = create(body);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("imageUrl", "Image URL must be a valid URL or empty"));
    }

    @Test
    void anEmptyImageUrlStillMeansNoImage() {
        // given
        ProductEntity product = setupProduct();

        // when
        ResponseEntity<Map<String, String>> response = update(product, Map.of("imageUrl", ""));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateRejectsABlankNameAndAWhitespaceOnlyCategory() {
        // given
        ProductEntity product = setupProduct();

        // when
        ResponseEntity<Map<String, String>> response = update(product, Map.of("name", "   ", "category", " "));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "name", "Product name is required",
                "category", "Category must not be blank"));
        ProductEntity stored = productRepository.findById(product.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo(product.getName());
        assertThat(stored.getCategory()).isEqualTo(product.getCategory());
    }

    @Test
    void anEmptyCategoryOnUpdateLeavesTheCategoryUnchanged() {
        // given
        ProductEntity product = setupProduct();

        // when
        ResponseEntity<Map<String, String>> response = update(product, Map.of("category", "", "name", "Renamed probe"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProductEntity stored = productRepository.findById(product.getId()).orElseThrow();
        assertThat(stored.getCategory()).isEqualTo(product.getCategory());
        assertThat(stored.getName()).isEqualTo("Renamed probe");
    }

    @Test
    void theCourseInvalidUpdateKeepsItsExactErrorMap() {
        // given: l20 admin/products/id.put.spec.ts
        ProductEntity product = setupProduct();
        Map<String, Object> body = new HashMap<>();
        body.put("name", "x");
        body.put("description", "");
        body.put("price", 0);
        body.put("stockQuantity", -1);
        body.put("category", "");
        body.put("imageUrl", "not-a-url");

        // when
        ResponseEntity<Map<String, String>> response = update(product, body);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "name", "Product name must be between 3 and 100 characters",
                "price", "Price must be greater than 0",
                "stockQuantity", "Stock quantity cannot be negative",
                "imageUrl", "Image URL must be a valid URL or empty"));
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
