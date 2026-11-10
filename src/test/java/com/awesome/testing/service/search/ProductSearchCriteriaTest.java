package com.awesome.testing.service.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSearchCriteriaTest {

    @Test
    void shouldApplyDefaultsWhenNothingIsGiven() {
        // when
        ProductSearchCriteria criteria = criteria(null, null, null, null, null, null, null, null, null);

        // then
        assertThat(criteria.likePattern()).isNull();
        assertThat(criteria.hasQuery()).isFalse();
        assertThat(criteria.categories()).isEmpty();
        assertThat(criteria.minPrice()).isNull();
        assertThat(criteria.maxPrice()).isNull();
        assertThat(criteria.minRating()).isNull();
        assertThat(criteria.inStockOnly()).isFalse();
        assertThat(criteria.sort()).isEqualTo(ProductSearchSort.NEWEST);
        assertThat(criteria.page()).isZero();
        assertThat(criteria.size()).isEqualTo(20);
    }

    @Test
    void shouldTreatBlankValuesAsAbsent() {
        // when
        ProductSearchCriteria criteria = criteria("   ", List.of(" ", ","), " ", "", " ", " ", " ", " ", " ");

        // then
        assertThat(criteria.hasQuery()).isFalse();
        assertThat(criteria.categories()).isEmpty();
        assertThat(criteria.minPrice()).isNull();
        assertThat(criteria.inStockOnly()).isFalse();
        assertThat(criteria.sort()).isEqualTo(ProductSearchSort.NEWEST);
        assertThat(criteria.page()).isZero();
        assertThat(criteria.size()).isEqualTo(20);
    }

    @Test
    void shouldTrimLowerCaseAndEscapeTheQuery() {
        // when
        ProductSearchCriteria criteria = criteria("  A%b_C\\d  ", null, null, null, null, null, null, null, null);

        // then
        assertThat(criteria.likePattern()).isEqualTo("%a\\%b\\_c\\\\d%");
        assertThat(criteria.hasQuery()).isTrue();
        assertThat(criteria.sort()).isEqualTo(ProductSearchSort.RELEVANCE);
    }

    @Test
    void shouldAcceptQueryOf100CharactersAfterTrimmingAndRejectLonger() {
        // given
        String longest = "x".repeat(100);

        // when
        ProductSearchCriteria accepted = criteria(" " + longest + " ", null, null, null, null, null, null, null, null);

        // then
        assertThat(accepted.likePattern()).isEqualTo("%" + longest + "%");
        assertInvalid(() -> criteria(longest + "y", null, null, null, null, null, null, null, null),
                Map.of("q", "must be at most 100 characters"));
    }

    @Test
    void shouldSplitTrimLowerCaseAndDeduplicateCategories() {
        // given
        List<String> values = new ArrayList<>(Arrays.asList(" Books , GARDEN", null, "books", ",tools,"));

        // when
        ProductSearchCriteria criteria = criteria(null, values, null, null, null, null, null, null, null);

        // then
        assertThat(criteria.categories()).containsExactly("books", "garden", "tools");
    }

    @Test
    void shouldAcceptTwentyCategoriesAndRejectMore() {
        // given
        List<String> twenty = IntStream.rangeClosed(1, 20).mapToObj(index -> "c" + index).toList();
        List<String> twentyOne = IntStream.rangeClosed(1, 21).mapToObj(index -> "c" + index).toList();

        // when
        ProductSearchCriteria criteria = criteria(null, twenty, null, null, null, null, null, null, null);

        // then
        assertThat(criteria.categories()).hasSize(20);
        assertInvalid(() -> criteria(null, twentyOne, null, null, null, null, null, null, null),
                Map.of("category", "must list at most 20 categories"));
    }

    @Test
    void shouldAcceptZeroAndEqualPriceBounds() {
        // when
        ProductSearchCriteria zero = criteria(null, null, "0", "0", null, null, null, null, null);
        ProductSearchCriteria range = criteria(null, null, " 10.50 ", "20", null, null, null, null, null);

        // then
        assertThat(zero.minPrice()).isZero();
        assertThat(zero.maxPrice()).isZero();
        assertThat(range.minPrice()).isEqualByComparingTo("10.50");
        assertThat(range.maxPrice()).isEqualByComparingTo("20");
    }

    @Test
    void shouldReportNegativeMinimumBeforeTheRangeOrder() {
        // when / then
        assertInvalid(() -> criteria(null, null, "-1", "-2", null, null, null, null, null),
                Map.of("minPrice", "must be zero or greater", "maxPrice", "must be zero or greater"));
        assertInvalid(() -> criteria(null, null, "20.01", "20", null, null, null, null, null),
                Map.of("minPrice", "must not be greater than maxPrice"));
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "5, 5", "4.5, 4.5"})
    void shouldAcceptRatingsFromOneToFive(String value, String expected) {
        // when
        ProductSearchCriteria criteria = criteria(null, null, null, null, value, null, null, null, null);

        // then
        assertThat(criteria.minRating()).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0.99", "5.01", "-3"})
    void shouldRejectRatingsOutsideOneToFive(String value) {
        // when / then
        assertInvalid(() -> criteria(null, null, null, null, value, null, null, null, null),
                Map.of("minRating", "must be between 1 and 5"));
    }

    @ParameterizedTest
    @CsvSource({
        "true, true", "TRUE, true", " true , true", "false, false", "False, false"
    })
    void shouldParseInStockFlag(String value, boolean expected) {
        // when
        ProductSearchCriteria criteria = criteria(null, null, null, null, null, value, null, null, null);

        // then
        assertThat(criteria.inStockOnly()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "relevance, RELEVANCE", " Price_Desc , PRICE_DESC", "price_asc, PRICE_ASC", "name_asc, NAME_ASC",
        "rating_desc, RATING_DESC", "newest, NEWEST"
    })
    void shouldParseSortCaseInsensitively(String value, ProductSearchSort expected) {
        // when
        ProductSearchCriteria criteria = criteria("lamp", null, null, null, null, null, value, null, null);

        // then
        assertThat(criteria.sort()).isEqualTo(expected);
    }

    @Test
    void shouldFallBackToNewestForRelevanceWithoutQuery() {
        // when
        ProductSearchCriteria criteria = criteria(null, null, null, null, null, null, "relevance", null, null);

        // then
        assertThat(criteria.sort()).isEqualTo(ProductSearchSort.NEWEST);
    }

    @Test
    void shouldNormalizeRelevanceWithoutQueryWhenConstructedDirectly() {
        // when
        ProductSearchCriteria withoutQuery = new ProductSearchCriteria(
                null, List.of(), null, null, null, false, ProductSearchSort.RELEVANCE, 0, 20);
        ProductSearchCriteria withQuery = new ProductSearchCriteria(
                "%a%", List.of(), null, null, null, false, ProductSearchSort.RELEVANCE, 0, 20);

        // then
        assertThat(withoutQuery.sort()).isEqualTo(ProductSearchSort.NEWEST);
        assertThat(withQuery.sort()).isEqualTo(ProductSearchSort.RELEVANCE);
    }

    @Test
    void shouldKeepExplicitSortWithoutQuery() {
        // when
        ProductSearchCriteria criteria = criteria(null, null, null, null, null, null, "price_asc", null, null);

        // then
        assertThat(criteria.sort()).isEqualTo(ProductSearchSort.PRICE_ASC);
    }

    @Test
    void shouldAcceptPageAndSizeBounds() {
        // when
        ProductSearchCriteria smallest = criteria(null, null, null, null, null, null, null, "0", "1");
        ProductSearchCriteria largest = criteria(null, null, null, null, null, null, null, " 7 ", "100");

        // then
        assertThat(smallest.page()).isZero();
        assertThat(smallest.size()).isEqualTo(1);
        assertThat(largest.page()).isEqualTo(7);
        assertThat(largest.size()).isEqualTo(100);
    }

    @Test
    void shouldRejectPageAndSizeOutOfRange() {
        // when / then
        assertInvalid(() -> criteria(null, null, null, null, null, null, null, "-1", "0"),
                Map.of("page", "must be zero or greater", "size", "must be between 1 and 100"));
        assertInvalid(() -> criteria(null, null, null, null, null, null, null, null, "101"),
                Map.of("size", "must be between 1 and 100"));
    }

    @Test
    void shouldRejectValuesOfTheWrongTypeByParameterName() {
        // when / then
        assertInvalid(() -> criteria(null, null, "1e", "ten", "five", "yes", "cheap", "1.5", "x"), Map.of(
                "minPrice", "must be a number",
                "maxPrice", "must be a number",
                "minRating", "must be a number",
                "inStock", "must be true or false",
                "sort", "must be one of relevance, newest, price_asc, price_desc, name_asc, rating_desc",
                "page", "must be an integer",
                "size", "must be an integer"));
    }

    @Test
    void shouldExposeImmutableCategories() {
        // given
        ProductSearchCriteria criteria = criteria(null, List.of("books"), null, null, null, null, null, null, null);

        // when / then
        assertThatThrownBy(() -> criteria.categories().add("garden")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldNameInvalidParametersInTheExceptionMessage() {
        // when / then
        assertThatThrownBy(() -> criteria(null, null, null, null, null, null, "bad", null, null))
                .isInstanceOf(ProductSearchValidationException.class)
                .hasMessage("Invalid product search parameters: [sort]");
    }

    @SuppressWarnings("java:S107")
    private static ProductSearchCriteria criteria(
            String q, List<String> category, String minPrice, String maxPrice, String minRating, String inStock,
            String sort, String page, String size) {
        return ProductSearchCriteria.parse(q, category, minPrice, maxPrice, minRating, inStock, sort, page, size);
    }

    private static void assertInvalid(Runnable action, Map<String, String> expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ProductSearchValidationException.class,
                        ex -> assertThat(ex.getErrors()).isEqualTo(expected));
    }
}
