package com.awesome.testing.repository.purchasing;

import com.awesome.testing.entity.purchasing.SupplierEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<SupplierEntity, Long> {

    boolean existsByNameIgnoreCase(String name);
}
