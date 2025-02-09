package com.awesome.testing.repository;

import com.awesome.testing.model.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {
    // JpaSpecificationExecutor allows for dynamic querying (filtering by category, price range, etc.)
} 