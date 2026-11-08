package com.awesome.testing.repository.review;

import com.awesome.testing.entity.review.ProductReviewEntity;
import com.awesome.testing.entity.review.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReviewEntity, Long> {

    Optional<ProductReviewEntity> findByProductIdAndUsername(Long productId, String username);

    Page<ProductReviewEntity> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "product")
    Page<ProductReviewEntity> findByStatus(ReviewStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "product")
    Page<ProductReviewEntity> findAllBy(Pageable pageable);

    @Query("select r.rating, count(r) from ProductReviewEntity r "
            + "where r.product.id = :productId and r.status = :status group by r.rating")
    List<Object[]> countByRating(@Param("productId") Long productId, @Param("status") ReviewStatus status);

    @Modifying
    @Query("delete from ProductReviewEntity r where r.username = :username")
    void deleteByUsername(@Param("username") String username);
}
