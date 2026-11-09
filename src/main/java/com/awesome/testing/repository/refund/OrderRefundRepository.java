package com.awesome.testing.repository.refund;

import com.awesome.testing.entity.refund.OrderRefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRefundRepository extends JpaRepository<OrderRefundEntity, Long> {

    Optional<OrderRefundEntity> findByRequestId(UUID requestId);
}
