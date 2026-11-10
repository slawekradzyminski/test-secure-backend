package com.awesome.testing.repository.payment;

import com.awesome.testing.entity.payment.OrderPaymentEntity;
import com.awesome.testing.entity.payment.OrderPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderPaymentRepository extends JpaRepository<OrderPaymentEntity, Long> {

    Optional<OrderPaymentEntity> findByPaymentId(UUID paymentId);

    Optional<OrderPaymentEntity> findFirstByOrderIdAndStatus(Long orderId, OrderPaymentStatus status);

    List<OrderPaymentEntity> findByOrderIdOrderByIdAsc(Long orderId);
}
