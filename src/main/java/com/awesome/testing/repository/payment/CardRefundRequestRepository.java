package com.awesome.testing.repository.payment;

import com.awesome.testing.entity.payment.CardRefundRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CardRefundRequestRepository extends JpaRepository<CardRefundRequestEntity, UUID> {

    List<CardRefundRequestEntity> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    /** Card amount of the order already requested back. */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM CardRefundRequestEntity r WHERE r.orderId = :orderId")
    BigDecimal sumAmountByOrderId(@Param("orderId") Long orderId);
}
