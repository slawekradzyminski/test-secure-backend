package com.awesome.testing.service.payment;

import com.awesome.testing.dto.payment.PaymentRefundRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRefundJobTest {

    @Mock private PaymentRefundOutbox outbox;
    @Mock private PaymentServiceClient client;
    @InjectMocks private PaymentRefundJob job;

    @Test
    void deliversEveryClaimedRefundAndRecordsTheOutcome() {
        PaymentRefundRequestDto request = new PaymentRefundRequestDto(UUID.randomUUID(), 1L, BigDecimal.ONE, "r");
        RefundDelivery outcome = RefundDelivery.refunded(UUID.randomUUID());
        when(outbox.claimDue(PaymentRefundJob.BATCH_SIZE)).thenReturn(List.of(new PaymentRefundOutbox.Delivery(7L, request)));
        when(client.refund(request)).thenReturn(outcome);

        assertThat(job.deliverDue()).isEqualTo(1);

        verify(outbox).record(7L, outcome);
    }

    @Test
    void theScheduleDoesNothingWhileDisabled() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.scheduledDelivery();

        verifyNoInteractions(outbox, client);
    }

    @Test
    void theScheduleDeliversWhenEnabled() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(outbox.claimDue(PaymentRefundJob.BATCH_SIZE)).thenReturn(List.of());

        job.scheduledDelivery();

        verify(outbox).claimDue(PaymentRefundJob.BATCH_SIZE);
    }
}
