package com.awesome.testing.repository;

import com.awesome.testing.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {
    // JpaSpecificationExecutor allows for dynamic querying (filtering by category, price range, etc.)

    Optional<ProductEntity> findFirstByNameIgnoreCaseOrderByIdAsc(String name);

    /** Products with an id greater than {@code afterId}, in id order (one-time inventory export). */
    @Query("select p from ProductEntity p where p.id > :afterId order by p.id")
    java.util.List<ProductEntity> findAfterId(@Param("afterId") long afterId, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductEntity p where p.id = :id")
    Optional<ProductEntity> findByIdForUpdate(@Param("id") Long id);
}
