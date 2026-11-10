package com.awesome.testing.repository.payment;

import com.awesome.testing.entity.payment.PaymentRefundOutboxEntity;
import com.awesome.testing.entity.payment.PaymentRefundStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundOutboxRepository extends JpaRepository<PaymentRefundOutboxEntity, Long> {

    Optional<PaymentRefundOutboxEntity> findByRequestId(UUID requestId);

    List<PaymentRefundOutboxEntity> findByOrderIdOrderByIdAsc(Long orderId);

    /** Card amount of the order's refunds that are refunded or still on their way. */
    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM PaymentRefundOutboxEntity r
            WHERE r.orderId = :orderId AND r.status IN :statuses
            """)
    BigDecimal sumAmountByOrderIdAndStatusIn(@Param("orderId") Long orderId,
                                             @Param("statuses") Collection<PaymentRefundStatus> statuses);

    @Query("""
            SELECT r FROM PaymentRefundOutboxEntity r
            WHERE r.status = :status AND r.nextAttemptAt <= :now
            ORDER BY r.nextAttemptAt ASC, r.id ASC
            """)
    List<PaymentRefundOutboxEntity> findDue(@Param("status") PaymentRefundStatus status,
                                            @Param("now") Instant now,
                                            Pageable pageable);
}
