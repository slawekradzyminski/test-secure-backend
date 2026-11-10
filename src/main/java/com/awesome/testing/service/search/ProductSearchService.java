package com.awesome.testing.service.search;

import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.dto.product.search.CategoryFacetDto;
import com.awesome.testing.dto.product.search.PriceRangeFacetDto;
import com.awesome.testing.dto.product.search.ProductSearchFacetsDto;
import com.awesome.testing.dto.product.search.ProductSearchItemDto;
import com.awesome.testing.dto.product.search.ProductSearchResultDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.search.ProductSearchQueries;
import com.awesome.testing.repository.search.ProductSearchQueries.CategoryAggregate;
import com.awesome.testing.repository.search.ProductSearchQueries.RatingAggregate;
import com.awesome.testing.repository.search.ProductSearchSpecifications;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Product search: one page query with the filters and ordering in SQL, one aggregate query for the ratings
 * of that page (no N+1), and one grouped query for the facets, which ignore the category filter.
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private static final Comparator<CategoryFacetDto> FACET_ORDER =
            Comparator.comparingLong(CategoryFacetDto::count).reversed().thenComparing(CategoryFacetDto::value);

    private final ProductRepository productRepository;
    private final ProductSearchQueries searchQueries;

    @Transactional(readOnly = true)
    public ProductSearchResultDto search(ProductSearchCriteria criteria) {
        Page<ProductEntity> page = productRepository.findAll(
                ProductSearchSpecifications.search(criteria), PageRequest.of(criteria.page(), criteria.size()));
        Map<Long, RatingAggregate> ratings = searchQueries
                .approvedRatings(page.getContent().stream().map(ProductEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(RatingAggregate::productId, Function.identity()));
        List<ProductSearchItemDto> content = page.getContent().stream()
                .map(product -> item(product, ratings.get(product.getId())))
                .toList();
        return new ProductSearchResultDto(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                facets(searchQueries.categoryAggregates(ProductSearchSpecifications.filters(criteria, false))));
    }

    static ProductSearchFacetsDto facets(List<CategoryAggregate> aggregates) {
        List<CategoryFacetDto> categories = aggregates.stream()
                .map(aggregate -> new CategoryFacetDto(aggregate.category(), aggregate.count()))
                .sorted(FACET_ORDER)
                .toList();
        BigDecimal min = aggregates.stream().map(CategoryAggregate::minPrice).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal max = aggregates.stream().map(CategoryAggregate::maxPrice).max(Comparator.naturalOrder()).orElse(null);
        long inStock = aggregates.stream().mapToLong(CategoryAggregate::inStock).sum();
        return new ProductSearchFacetsDto(categories, new PriceRangeFacetDto(min, max), inStock);
    }

    static BigDecimal averageRating(RatingAggregate rating) {
        return BigDecimal.valueOf(rating.ratingSum())
                .divide(BigDecimal.valueOf(rating.reviewCount()), 1, RoundingMode.HALF_UP);
    }

    private static ProductSearchItemDto item(ProductEntity product, RatingAggregate rating) {
        return rating == null
                ? ProductSearchItemDto.from(ProductDto.from(product), null, 0)
                : ProductSearchItemDto.from(ProductDto.from(product), averageRating(rating), rating.reviewCount());
    }
}
