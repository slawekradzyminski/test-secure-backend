package com.awesome.testing.repository.search;

import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.review.ReviewStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/** Aggregate queries of product search: one for the facets, one for the ratings of a page. */
@Repository
public class ProductSearchQueries {

    @PersistenceContext
    private EntityManager entityManager;

    /** Per-category counts, price bounds and in-stock counts of the products matching {@code filter}. */
    public List<CategoryAggregate> categoryAggregates(Specification<ProductEntity> filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<ProductEntity> product = query.from(ProductEntity.class);
        Expression<String> category = product.get("category");
        Expression<BigDecimal> price = product.get("price");
        Expression<Long> inStock = cb.<Long>selectCase()
                .when(cb.greaterThan(product.get("stockQuantity"), 0), 1L)
                .otherwise(0L);
        query.multiselect(category, cb.count(product), cb.min(price), cb.max(price), cb.sum(inStock))
                .groupBy(category);
        Predicate predicate = filter.toPredicate(product, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
        return entityManager.createQuery(query).getResultStream()
                .map(row -> new CategoryAggregate(
                        row.get(0, String.class),
                        row.get(1, Long.class),
                        row.get(2, BigDecimal.class),
                        row.get(3, BigDecimal.class),
                        row.get(4, Number.class).longValue()))
                .toList();
    }

    /** Sum and count of approved ratings for each listed product that has any. */
    public List<RatingAggregate> approvedRatings(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "select r.product.id, sum(r.rating), count(r) from ProductReviewEntity r "
                                + "where r.status = :status and r.product.id in :ids group by r.product.id",
                        Object[].class)
                .setParameter("status", ReviewStatus.APPROVED)
                .setParameter("ids", productIds)
                .getResultStream()
                .map(row -> new RatingAggregate(
                        ((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue()))
                .toList();
    }

    public record CategoryAggregate(String category, long count, BigDecimal minPrice, BigDecimal maxPrice, long inStock) {}

    public record RatingAggregate(long productId, long ratingSum, long reviewCount) {}
}
