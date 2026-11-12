package com.awesome.testing.service.search;

import com.awesome.testing.dto.product.search.CategoryFacetDto;
import com.awesome.testing.dto.product.search.ProductSearchItemDto;
import com.awesome.testing.dto.product.search.ProductSearchResultDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.review.ProductReviewEntity;
import com.awesome.testing.entity.review.ReviewStatus;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.review.ProductReviewRepository;
import com.awesome.testing.repository.search.ProductSearchQueries;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the search SQL (LIKE ESCAPE, CASE relevance, rating subqueries, facets) and V15 on PostgreSQL. */
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ProductSearchService.class, ProductSearchQueries.class})
class ProductSearchPostgresIT {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:16-alpine@sha256:"
                                            + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                            .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private ProductSearchService searchService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductReviewRepository reviewRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @BeforeEach
    void emptyCatalog() {
        reviewRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    @Test
    void shouldCreateTheSearchIndexes() {
        // when
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'products'", String.class);

        // then
        assertThat(indexes).contains("idx_products_lower_name_id", "idx_products_price_id", "idx_products_created_at_id");
    }

    @Test
    void shouldRankEscapeFilterAndFacetOnPostgres() {
        // given
        ProductEntity armchair = product("Armchair", "Comfortable chair", "Chairs", "120.00", 2);
        review(armchair, 5);
        review(armchair, 4);
        product("Chair", "Plain", "Misc", "40.00", 0);
        product("Sofa", "Fits a chair", "Misc", "300.00", 1);
        product("100% chair", "Sale", "Misc", "10.00", 1);

        // when
        ProductSearchResultDto relevance = search("chair", null, null, null, null, null);
        ProductSearchResultDto percent = search("100%", null, null, null, null, null);
        ProductSearchResultDto rated = search(null, "chairs", "4.5", null, "true", "rating_desc");

        // then
        assertThat(names(relevance)).containsExactly("Armchair", "100% chair", "Chair", "Sofa");
        assertThat(names(percent)).containsExactly("100% chair");
        assertThat(names(rated)).containsExactly("Armchair");
        assertThat(rated.content().getFirst().getAverageRating()).isEqualByComparingTo("4.5");
        assertThat(rated.facets().categories()).containsExactly(new CategoryFacetDto("Chairs", 1));
        assertThat(rated.facets().priceRange().min()).isEqualByComparingTo("120.00");
        assertThat(rated.facets().inStockCount()).isEqualTo(1);
    }

    @Test
    void shouldSortEveryWayOnPostgres() {
        // given
        ProductEntity rated = product("banana", "x", "Fruit", "2.00", 1);
        review(rated, 3);
        product("Apple", "x", "Fruit", "3.00", 1);
        product("cherry", "x", "Fruit", "1.00", 1);

        // when / then
        assertThat(names(search(null, null, null, null, null, "price_asc"))).containsExactly("cherry", "banana", "Apple");
        assertThat(names(search(null, null, null, null, null, "price_desc"))).containsExactly("Apple", "banana", "cherry");
        assertThat(names(search(null, null, null, null, null, "name_asc"))).containsExactly("Apple", "banana", "cherry");
        assertThat(names(search(null, null, null, null, null, "newest"))).containsExactly("cherry", "Apple", "banana");
        assertThat(names(search(null, null, null, null, null, "rating_desc")).getFirst()).isEqualTo("banana");
    }

    private ProductSearchResultDto search(
            String q, String category, String minRating, String maxPrice, String inStock, String sort) {
        return searchService.search(ProductSearchCriteria.parse(
                q, category == null ? null : List.of(category), null, maxPrice, minRating, inStock, sort, null, null));
    }

    private ProductEntity product(String name, String description, String category, String price, int stock) {
        return productRepository.saveAndFlush(ProductEntity.builder()
                .name(name).description(description).category(category)
                .price(new BigDecimal(price)).stockQuantity(stock).build());
    }

    private void review(ProductEntity product, int rating) {
        reviewRepository.saveAndFlush(ProductReviewEntity.builder()
                .product(product).username("reviewer-" + UUID.randomUUID()).rating(rating)
                .title("Review").body("Body").status(ReviewStatus.APPROVED).build());
    }

    private static List<String> names(ProductSearchResultDto result) {
        return result.content().stream().map(ProductSearchItemDto::getName).toList();
    }

    @Test
    void shouldIgnoreDiacriticsOnPostgres() {
        // given
        product("Zażółć Gęślą Jaźń", "Polish pangram", "Books", "10.00", 1);
        product("Plain", "Nothing special", "Misc", "30.00", 1);

        // when / then
        assertThat(names(search("zazolc", null, null, null, null, null))).containsExactly("Zażółć Gęślą Jaźń");
        assertThat(names(search("ZAŻÓŁĆ", null, null, null, null, null))).containsExactly("Zażółć Gęślą Jaźń");
        assertThat(names(search("jazn", null, null, null, null, null))).containsExactly("Zażółć Gęślą Jaźń");
    }
}
