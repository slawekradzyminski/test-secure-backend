package com.awesome.testing.repository.purchasing;

import com.awesome.testing.entity.purchasing.PurchaseOrderEntity;
import com.awesome.testing.entity.purchasing.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, Long> {

    @EntityGraph(attributePaths = "supplier")
    Page<PurchaseOrderEntity> findByStatus(PurchaseOrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "supplier")
    Page<PurchaseOrderEntity> findAllBy(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrderEntity p where p.id = :id")
    Optional<PurchaseOrderEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("select l.product.id, sum(l.orderedQuantity - l.receivedQuantity) from PurchaseOrderLineEntity l "
            + "where l.purchaseOrder.status in :statuses group by l.product.id")
    List<Object[]> sumOutstandingByProduct(@Param("statuses") Collection<PurchaseOrderStatus> statuses);
}
