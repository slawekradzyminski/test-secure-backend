package com.awesome.testing.repository.inventory;

import com.awesome.testing.entity.inventory.InventoryMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovementEntity, Long> {

    Optional<InventoryMovementEntity> findByProductIdAndRequestId(Long productId, UUID requestId);

    /** Movements written for one receipt request, across all of its products. */
    @Query("SELECT m FROM InventoryMovementEntity m JOIN FETCH m.product WHERE m.requestId = :requestId ORDER BY m.id")
    List<InventoryMovementEntity> findByRequestId(@Param("requestId") UUID requestId);

    Page<InventoryMovementEntity> findByProductIdOrderByCreatedAtDescIdDesc(
            Long productId,
            Pageable pageable);

    /** Movements of the given products in id order, with their order ids (one-time export). */
    @Query("SELECT m FROM InventoryMovementEntity m LEFT JOIN FETCH m.order JOIN FETCH m.product p"
            + " WHERE p.id IN :productIds ORDER BY m.id")
    List<InventoryMovementEntity> findByProductIdsInIdOrder(@Param("productIds") java.util.Collection<Long> productIds);
}
