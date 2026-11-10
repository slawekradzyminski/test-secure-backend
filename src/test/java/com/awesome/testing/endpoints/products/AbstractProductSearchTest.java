package com.awesome.testing.endpoints.products;

import com.awesome.testing.dto.product.search.ProductSearchItemDto;
import com.awesome.testing.dto.product.search.ProductSearchResultDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.review.ProductReviewEntity;
import com.awesome.testing.entity.review.ReviewStatus;
import com.awesome.testing.repository.review.ProductReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;

abstract class AbstractProductSearchTest extends AbstractEcommerceTest {

    protected static final String SEARCH_ENDPOINT = PRODUCTS_ENDPOINT + "/search";

    @Autowired
    protected ProductReviewRepository productReviewRepository;

    protected String clientToken;

    @BeforeEach
    void authenticate() {
        clientToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
    }

    protected ProductEntity product(String name, String description, String category, String price, int stock) {
        return productRepository.save(ProductEntity.builder()
                .name(name)
                .description(description)
                .category(category)
                .price(new BigDecimal(price))
                .stockQuantity(stock)
                .build());
    }

    protected void review(ProductEntity product, int rating, ReviewStatus status) {
        productReviewRepository.save(ProductReviewEntity.builder()
                .product(product)
                .username("reviewer-" + UUID.randomUUID())
                .rating(rating)
                .title("Review")
                .body("Review body")
                .status(status)
                .build());
    }

    protected ResponseEntity<ProductSearchResultDto> search(String queryString) {
        return executeGet(SEARCH_ENDPOINT + queryString, getHeadersWith(clientToken), ProductSearchResultDto.class);
    }

    protected ResponseEntity<Map<String, String>> searchExpectingError(String queryString) {
        return executeGet(SEARCH_ENDPOINT + queryString, getHeadersWith(clientToken), mapTypeReference());
    }

    protected static List<String> names(ResponseEntity<ProductSearchResultDto> response) {
        return response.getBody().content().stream().map(ProductSearchItemDto::getName).toList();
    }
}
