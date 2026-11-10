package com.awesome.testing.service.search;

import com.awesome.testing.dto.product.search.CategoryFacetDto;
import com.awesome.testing.dto.product.search.ProductSearchFacetsDto;
import com.awesome.testing.repository.search.ProductSearchQueries.CategoryAggregate;
import com.awesome.testing.repository.search.ProductSearchQueries.RatingAggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchServiceTest {

    @Test
    void shouldMergeCategoryAggregatesIntoFacets() {
        // given
        List<CategoryAggregate> aggregates = List.of(
                new CategoryAggregate("Garden", 2, new BigDecimal("15.00"), new BigDecimal("40.00"), 1),
                new CategoryAggregate("Books", 2, new BigDecimal("8.00"), new BigDecimal("30.00"), 2),
                new CategoryAggregate("Tools", 5, new BigDecimal("20.00"), new BigDecimal("99.00"), 0));

        // when
        ProductSearchFacetsDto facets = ProductSearchService.facets(aggregates);

        // then
        assertThat(facets.categories()).containsExactly(
                new CategoryFacetDto("Tools", 5), new CategoryFacetDto("Books", 2), new CategoryFacetDto("Garden", 2));
        assertThat(facets.priceRange().min()).isEqualByComparingTo("8.00");
        assertThat(facets.priceRange().max()).isEqualByComparingTo("99.00");
        assertThat(facets.inStockCount()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyFacetsWithoutProducts() {
        // when
        ProductSearchFacetsDto facets = ProductSearchService.facets(List.of());

        // then
        assertThat(facets.categories()).isEmpty();
        assertThat(facets.priceRange().min()).isNull();
        assertThat(facets.priceRange().max()).isNull();
        assertThat(facets.inStockCount()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"13, 3, 4.3", "17, 4, 4.3", "9, 2, 4.5", "29, 6, 4.8", "5, 1, 5.0"})
    void shouldRoundAverageRatingHalfUpToOneDecimal(long sum, long count, String expected) {
        // when
        BigDecimal average = ProductSearchService.averageRating(new RatingAggregate(1L, sum, count));

        // then
        assertThat(average).isEqualTo(new BigDecimal(expected));
    }
}
