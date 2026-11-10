package com.awesome.testing.service.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Delivers due card refunds from {@link PaymentRefundOutbox} to payment-service.
 * {@code payment.refunds.delivery.enabled} switches the schedule off (tests
 * call {@link #deliverDue()} directly).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundJob {

    static final int BATCH_SIZE = 20;

    private final PaymentRefundOutbox outbox;
    private final PaymentServiceClient paymentServiceClient;

    @Value("${payment.refunds.delivery.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${payment.refunds.delivery.interval:PT15S}",
            initialDelayString = "${payment.refunds.delivery.initial-delay:PT30S}")
    public void scheduledDelivery() {
        if (enabled) {
            deliverDue();
        }
    }

    /** Delivers every claimed due refund once; returns how many were attempted. */
    public int deliverDue() {
        List<PaymentRefundOutbox.Delivery> due = outbox.claimDue(BATCH_SIZE);
        if (!due.isEmpty()) {
            log.info("Delivering {} card refund(s) to payment-service", due.size());
        }
        for (PaymentRefundOutbox.Delivery delivery : due) {
            outbox.record(delivery.id(), paymentServiceClient.refund(delivery.request()));
        }
        return due.size();
    }
}
