package com.awesome.testing.repository;

import com.awesome.testing.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {
    // JpaSpecificationExecutor allows for dynamic querying (filtering by category, price range, etc.)

    Optional<ProductEntity> findFirstByNameIgnoreCaseOrderByIdAsc(String name);
}
