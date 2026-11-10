package com.awesome.testing.service.payment;

import com.awesome.testing.dto.payment.PaymentRefundRequestDto;
import com.awesome.testing.entity.payment.PaymentRefundOutboxEntity;
import com.awesome.testing.entity.payment.PaymentRefundStatus;
import com.awesome.testing.repository.payment.PaymentRefundOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Durable queue of card refunds for payment-service. Rows are written in the
 * transaction that booked the refund and delivered by {@link PaymentRefundJob}
 * with exponential backoff (30 s doubling up to 30 min, ten attempts in total).
 * A claimed row is leased for {@link #CLAIM_LEASE} so a crash mid-delivery only
 * delays it; payment-service deduplicates the requestId.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundOutbox {

    static final int MAX_ATTEMPTS = 10;
    static final Duration FIRST_BACKOFF = Duration.ofSeconds(30);
    static final Duration MAX_BACKOFF = Duration.ofMinutes(30);
    static final Duration CLAIM_LEASE = Duration.ofMinutes(5);
    /** Card refunds that count against the captured amount: delivered or still on their way. */
    static final Set<PaymentRefundStatus> COUNTED = EnumSet.of(PaymentRefundStatus.PENDING, PaymentRefundStatus.SENT);

    private final PaymentRefundOutboxRepository repository;
    private final Clock commerceClock;

    /** A claimed row to deliver now. */
    public record Delivery(Long id, PaymentRefundRequestDto request) {
    }

    /** Card amount of the order already refunded or queued for refund. */
    @Transactional(readOnly = true)
    public BigDecimal cardRefunded(Long orderId) {
        return repository.sumAmountByOrderIdAndStatusIn(orderId, COUNTED);
    }

    /** True when a card refund with this requestId was already queued (in any state). */
    @Transactional(readOnly = true)
    public boolean isQueued(UUID requestId) {
        return repository.findByRequestId(requestId).isPresent();
    }

    /** Queues a card refund; due immediately. Call inside the transaction that booked the refund. */
    @Transactional
    public void enqueue(Long orderId, UUID paymentId, UUID requestId, BigDecimal amount, String reason) {
        Instant now = commerceClock.instant();
        repository.save(PaymentRefundOutboxEntity.builder()
                .requestId(requestId)
                .orderId(orderId)
                .paymentId(paymentId)
                .amount(amount)
                .reason(reason)
                .status(PaymentRefundStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    /** Claims up to {@code limit} due rows, oldest first, leasing them so they are not claimed twice. */
    @Transactional
    public List<Delivery> claimDue(int limit) {
        Instant now = commerceClock.instant();
        return repository.findDue(PaymentRefundStatus.PENDING, now, PageRequest.of(0, limit)).stream()
                .map(row -> {
                    row.setNextAttemptAt(now.plus(CLAIM_LEASE));
                    row.setUpdatedAt(now);
                    repository.save(row);
                    return new Delivery(row.getId(), new PaymentRefundRequestDto(
                            row.getRequestId(), row.getOrderId(), row.getAmount(), row.getReason()));
                })
                .toList();
    }

    /** Records the result of one delivery attempt. */
    @Transactional
    public void record(Long id, RefundDelivery delivery) {
        repository.findById(id).ifPresent(row -> {
            Instant now = commerceClock.instant();
            int attempts = row.getAttempts() + 1;
            row.setAttempts(attempts);
            row.setUpdatedAt(now);
            row.setLastError(delivery.outcome() == RefundDelivery.Outcome.REFUNDED ? null : truncate(delivery.detail()));
            switch (delivery.outcome()) {
                case REFUNDED -> {
                    row.setStatus(PaymentRefundStatus.SENT);
                    row.setRefundId(delivery.refundId());
                    row.setNextAttemptAt(null);
                }
                case NO_CAPTURED_PAYMENT -> {
                    row.setStatus(PaymentRefundStatus.SKIPPED);
                    row.setNextAttemptAt(null);
                    log.info("payment-service has no captured payment for order {}; card refund {} skipped",
                            row.getOrderId(), row.getRequestId());
                }
                case REJECTED -> {
                    row.setStatus(PaymentRefundStatus.FAILED);
                    row.setNextAttemptAt(null);
                    log.error("payment-service rejected card refund {} for order {}: {}",
                            row.getRequestId(), row.getOrderId(), row.getLastError());
                }
                case RETRY -> {
                    if (attempts >= MAX_ATTEMPTS) {
                        row.setStatus(PaymentRefundStatus.FAILED);
                        row.setNextAttemptAt(null);
                        log.error("Card refund {} for order {} failed after {} attempts: {}",
                                row.getRequestId(), row.getOrderId(), attempts, row.getLastError());
                    } else {
                        row.setNextAttemptAt(now.plus(backoff(attempts)));
                        log.warn("Card refund {} for order {} will be retried (attempt {}): {}",
                                row.getRequestId(), row.getOrderId(), attempts, row.getLastError());
                    }
                }
            }
            repository.save(row);
        });
    }

    /** 30 s after the first failed attempt, doubling, at most 30 minutes. */
    static Duration backoff(int attempts) {
        Duration delay = FIRST_BACKOFF.multipliedBy(1L << Math.min(attempts - 1, 16));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}
