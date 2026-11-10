package com.awesome.testing.repository.search;

import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.review.ProductReviewEntity;
import com.awesome.testing.entity.review.ReviewStatus;
import com.awesome.testing.service.search.ProductSearchCriteria;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Portable (H2 and PostgreSQL) criteria for product search: {@code lower(column) LIKE ? ESCAPE '\'} text
 * matching, correlated subqueries over approved reviews for ratings, and a {@code CASE} relevance score.
 * Ordering lives in the specification because relevance and rating are expressions, not entity properties;
 * the page request must therefore be unsorted.
 */
public final class ProductSearchSpecifications {
    static final int NAME_WEIGHT = 4;
    static final int CATEGORY_WEIGHT = 2;
    static final int DESCRIPTION_WEIGHT = 1;

    private ProductSearchSpecifications() {}

    /** Every filter of the criteria, ordered by the requested sort (the count query drops the ordering). */
    public static Specification<ProductEntity> search(ProductSearchCriteria criteria) {
        return (root, query, cb) -> {
            if (ProductEntity.class.equals(query.getResultType())) {
                query.orderBy(orders(criteria, root, query, cb));
            }
            return filters(criteria, true).toPredicate(root, query, cb);
        };
    }

    /** Filters only; {@code includeCategory = false} gives the base of the facets. */
    public static Specification<ProductEntity> filters(ProductSearchCriteria criteria, boolean includeCategory) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.hasQuery()) {
                predicates.add(cb.or(
                        matches(cb, root.get("name"), criteria),
                        matches(cb, root.get("category"), criteria),
                        matches(cb, root.get("description"), criteria)));
            }
            if (includeCategory && !criteria.categories().isEmpty()) {
                predicates.add(cb.lower(root.get("category")).in(criteria.categories()));
            }
            if (criteria.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), criteria.maxPrice()));
            }
            if (criteria.inStockOnly()) {
                predicates.add(cb.greaterThan(root.get("stockQuantity"), 0));
            }
            if (criteria.minRating() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        averageRating(root, query, cb), criteria.minRating().doubleValue()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static List<Order> orders(
            ProductSearchCriteria criteria, Root<ProductEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Expression<String> name = cb.lower(root.get("name"));
        Order byId = cb.asc(root.get("id"));
        return switch (criteria.sort()) {
            case RELEVANCE -> List.of(cb.desc(relevance(criteria, root, cb)), cb.asc(name), byId);
            case PRICE_ASC -> List.of(cb.asc(root.get("price")), byId);
            case PRICE_DESC -> List.of(cb.desc(root.get("price")), byId);
            case NAME_ASC -> List.of(cb.asc(name), byId);
            case RATING_DESC -> List.of(
                    cb.desc(cb.coalesce(averageRating(root, query, cb), 0.0)),
                    cb.desc(reviewCount(root, query, cb)),
                    cb.asc(name),
                    byId);
            case NEWEST -> List.of(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
        };
    }

    /**
     * Name match scores highest, then category, then description; a product can match all three.
     * {@link ProductSearchCriteria} only selects relevance together with a text query.
     */
    private static Expression<Integer> relevance(
            ProductSearchCriteria criteria, Root<ProductEntity> root, CriteriaBuilder cb) {
        return cb.sum(
                cb.sum(
                        weight(cb, matches(cb, root.get("name"), criteria), NAME_WEIGHT),
                        weight(cb, matches(cb, root.get("category"), criteria), CATEGORY_WEIGHT)),
                weight(cb, matches(cb, root.get("description"), criteria), DESCRIPTION_WEIGHT));
    }

    private static Expression<Integer> weight(CriteriaBuilder cb, Predicate match, int weight) {
        return cb.<Integer>selectCase().when(match, weight).otherwise(0);
    }

    private static Predicate matches(CriteriaBuilder cb, Expression<String> column, ProductSearchCriteria criteria) {
        return cb.like(cb.lower(column), criteria.likePattern(), ProductSearchCriteria.LIKE_ESCAPE);
    }

    private static Subquery<Double> averageRating(Root<ProductEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Double> average = query.subquery(Double.class);
        Root<ProductReviewEntity> review = average.from(ProductReviewEntity.class);
        return average.select(cb.avg(review.get("rating"))).where(approvedReviewOf(root, review, cb));
    }

    private static Subquery<Long> reviewCount(Root<ProductEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Long> count = query.subquery(Long.class);
        Root<ProductReviewEntity> review = count.from(ProductReviewEntity.class);
        return count.select(cb.count(review)).where(approvedReviewOf(root, review, cb));
    }

    private static Predicate approvedReviewOf(
            Root<ProductEntity> product, Root<ProductReviewEntity> review, CriteriaBuilder cb) {
        return cb.and(
                cb.equal(review.get("product"), product),
                cb.equal(review.get("status"), ReviewStatus.APPROVED));
    }
}
