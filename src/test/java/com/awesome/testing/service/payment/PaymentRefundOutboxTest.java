package com.awesome.testing.service.payment;

import com.awesome.testing.entity.payment.PaymentRefundOutboxEntity;
import com.awesome.testing.entity.payment.PaymentRefundStatus;
import com.awesome.testing.repository.payment.PaymentRefundOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRefundOutboxTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(600);
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PAYMENT = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    @Mock private PaymentRefundOutboxRepository repository;

    private PaymentRefundOutbox outbox;
    private PaymentRefundOutboxEntity row;

    @BeforeEach
    void setUp() {
        outbox = new PaymentRefundOutbox(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        row = PaymentRefundOutboxEntity.builder().id(1L).requestId(REQUEST).orderId(4L).paymentId(PAYMENT)
                .amount(new BigDecimal("9.99")).reason("Return #2").status(PaymentRefundStatus.PENDING)
                .attempts(0).nextAttemptAt(NOW).createdAt(EARLIER).updatedAt(EARLIER).build();
        lenient().when(repository.findById(1L)).thenReturn(Optional.of(row));
        lenient().when(repository.save(any(PaymentRefundOutboxEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enqueueWritesAPendingRowDueNow() {
        outbox.enqueue(4L, PAYMENT, REQUEST, new BigDecimal("9.99"), "Return #2");

        ArgumentCaptor<PaymentRefundOutboxEntity> captor = ArgumentCaptor.forClass(PaymentRefundOutboxEntity.class);
        verify(repository).save(captor.capture());
        PaymentRefundOutboxEntity saved = captor.getValue();
        assertThat(saved.getRequestId()).isEqualTo(REQUEST);
        assertThat(saved.getOrderId()).isEqualTo(4L);
        assertThat(saved.getPaymentId()).isEqualTo(PAYMENT);
        assertThat(saved.getAmount()).isEqualTo(new BigDecimal("9.99"));
        assertThat(saved.getReason()).isEqualTo("Return #2");
        assertThat(saved.getStatus()).isEqualTo(PaymentRefundStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void isQueuedLooksUpTheRequestId() {
        UUID other = UUID.randomUUID();
        when(repository.findByRequestId(REQUEST)).thenReturn(Optional.of(row));
        when(repository.findByRequestId(other)).thenReturn(Optional.empty());

        assertThat(outbox.isQueued(REQUEST)).isTrue();
        assertThat(outbox.isQueued(other)).isFalse();
    }

    @Test
    void cardRefundedCountsPendingAndSentRows() {
        when(repository.sumAmountByOrderIdAndStatusIn(4L, EnumSet.of(PaymentRefundStatus.PENDING, PaymentRefundStatus.SENT)))
                .thenReturn(new BigDecimal("3.00"));

        assertThat(outbox.cardRefunded(4L)).isEqualTo(new BigDecimal("3.00"));
    }

    @Test
    void claimLeasesDueRowsAndReturnsTheirRequests() {
        when(repository.findDue(PaymentRefundStatus.PENDING, NOW, PageRequest.of(0, 20))).thenReturn(List.of(row));

        List<PaymentRefundOutbox.Delivery> claimed = outbox.claimDue(20);

        assertThat(claimed).singleElement().satisfies(delivery -> {
            assertThat(delivery.id()).isEqualTo(1L);
            assertThat(delivery.request().requestId()).isEqualTo(REQUEST);
            assertThat(delivery.request().orderId()).isEqualTo(4L);
            assertThat(delivery.request().amount()).isEqualTo(new BigDecimal("9.99"));
            assertThat(delivery.request().reason()).isEqualTo("Return #2");
        });
        assertThat(row.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(row.getStatus()).isEqualTo(PaymentRefundStatus.PENDING);
        assertThat(row.getUpdatedAt()).isEqualTo(NOW);
        verify(repository).save(row);
    }

    @Test
    void aBookedRefundIsSent() {
        UUID refundId = UUID.randomUUID();
        row.setLastError("HTTP 503");

        outbox.record(1L, RefundDelivery.refunded(refundId));

        assertThat(row.getStatus()).isEqualTo(PaymentRefundStatus.SENT);
        assertThat(row.getRefundId()).isEqualTo(refundId);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isNull();
        assertThat(row.getLastError()).isNull();
        assertThat(row.getUpdatedAt()).isEqualTo(NOW);
        verify(repository).save(row);
    }

    @Test
    void noCapturedPaymentIsSkipped() {
        outbox.record(1L, RefundDelivery.withOutcome(RefundDelivery.Outcome.NO_CAPTURED_PAYMENT, "HTTP 404"));

        assertThat(row.getStatus()).isEqualTo(PaymentRefundStatus.SKIPPED);
        assertThat(row.getNextAttemptAt()).isNull();
        assertThat(row.getLastError()).isEqualTo("HTTP 404");
        assertThat(row.getRefundId()).isNull();
    }

    @Test
    void rejectionFailsImmediately() {
        outbox.record(1L, RefundDelivery.withOutcome(RefundDelivery.Outcome.REJECTED, "HTTP 409"));

        assertThat(row.getStatus()).isEqualTo(PaymentRefundStatus.FAILED);
        assertThat(row.getNextAttemptAt()).isNull();
        assertThat(row.getAttempts()).isEqualTo(1);
    }

    @Test
    void retriesBackOffAndGiveUpAfterTenAttempts() {
        outbox.record(1L, RefundDelivery.withOutcome(RefundDelivery.Outcome.RETRY, "x".repeat(1200)));

        assertThat(row.getStatus()).isEqualTo(PaymentRefundStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(row.getLastError()).hasSize(1000);

        row.setAttempts(8);
        outbox.record(1L, RefundDelivery.withOutcome(RefundDelivery.Outcome.RETRY, "HTTP 503"));
        assertThat(row.getStatus()).isEqualTo(PaymentRefundStatus.PENDING);
        assertThat(row.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));

        outbox.record(1L, RefundDelivery.withOutcome(RefundDelivery.Outcome.RETRY, "HTTP 503"));
        assertThat(row.getAttempts()).isEqualTo(10);
        assertThat(row.getStatus()).isEqualTo(PaymentRefundStatus.FAILED);
        assertThat(row.getNextAttemptAt()).isNull();
    }

    @Test
    void backoffDoublesFromThirtySecondsUpToThirtyMinutes() {
        assertThat(PaymentRefundOutbox.backoff(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(PaymentRefundOutbox.backoff(2)).isEqualTo(Duration.ofMinutes(1));
        assertThat(PaymentRefundOutbox.backoff(3)).isEqualTo(Duration.ofMinutes(2));
        assertThat(PaymentRefundOutbox.backoff(6)).isEqualTo(Duration.ofMinutes(16));
        assertThat(PaymentRefundOutbox.backoff(7)).isEqualTo(Duration.ofMinutes(30));
        assertThat(PaymentRefundOutbox.backoff(60)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void unknownRowsAreIgnored() {
        when(repository.findById(2L)).thenReturn(Optional.empty());

        outbox.record(2L, RefundDelivery.refunded(UUID.randomUUID()));

        verify(repository, never()).save(any());
    }
}
