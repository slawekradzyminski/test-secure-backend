package com.awesome.testing.repository.inventory;

import com.awesome.testing.entity.inventory.InventoryMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovementEntity, Long> {

    Optional<InventoryMovementEntity> findByProductIdAndRequestId(Long productId, UUID requestId);

    Page<InventoryMovementEntity> findByProductIdOrderByCreatedAtDescIdDesc(
            Long productId,
            Pageable pageable);
}
