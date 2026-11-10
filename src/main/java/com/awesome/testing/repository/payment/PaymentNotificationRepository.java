package com.awesome.testing.repository.payment;

import com.awesome.testing.entity.payment.PaymentNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentNotificationRepository extends JpaRepository<PaymentNotificationEntity, Long> {

    Optional<PaymentNotificationEntity> findByRequestId(UUID requestId);
}
