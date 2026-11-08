package com.awesome.testing.repository.fulfilment;

import com.awesome.testing.entity.fulfilment.ShipmentEntity;
import com.awesome.testing.entity.fulfilment.ShipmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<ShipmentEntity, Long> {

    Optional<ShipmentEntity> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShipmentEntity s where s.id = :id")
    Optional<ShipmentEntity> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "order")
    Page<ShipmentEntity> findByStatus(ShipmentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "order")
    Page<ShipmentEntity> findAllBy(Pageable pageable);
}
