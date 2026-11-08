package com.awesome.testing.repository.purchasing;

import com.awesome.testing.entity.purchasing.PurchaseReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceiptEntity, Long> {

    Optional<PurchaseReceiptEntity> findByPurchaseOrderIdAndRequestId(Long purchaseOrderId, UUID requestId);
}
