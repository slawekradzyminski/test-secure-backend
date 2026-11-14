package com.awesome.testing.service.email;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.awesome.testing.correlation.CorrelationTracing;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailRetryJobTest {

    @Mock private EmailEventService emailEventService;
    @Mock private EmailService emailService;
    @Spy private CorrelationTracing correlationTracing = CorrelationTracing.disabled();
    @InjectMocks private EmailRetryJob job;

    @Test
    void redeliversEveryClaimedEmail() {
        ReflectionTestUtils.setField(job, "enabled", true);
        EmailDto email = EmailDto.builder().to("a@example.com").subject("s").message("m").build();
        when(emailEventService.claimDue(EmailRetryJob.BATCH_SIZE)).thenReturn(List.of(
                new EmailEventService.Redelivery(1, email, "email", null, null),
                new EmailEventService.Redelivery(2, email, "email", null, null)));

        job.retryDueEmails();

        verify(emailService).redeliver(1, email, "email");
        verify(emailService).redeliver(2, email, "email");
    }

    @Test
    void redeliversUnderTheIdsOfTheRequestThatQueuedTheEmail() {
        ReflectionTestUtils.setField(job, "enabled", true);
        EmailDto email = EmailDto.builder().to("a@example.com").subject("s").message("m").build();
        String trace = "4bf92f3577b34da6a3ce929d0e0e4736";
        when(emailEventService.claimDue(EmailRetryJob.BATCH_SIZE)).thenReturn(List.of(
                new EmailEventService.Redelivery(1, email, "email", "queued-req", "00-" + trace + "-00f067aa0ba902b7-01"),
                new EmailEventService.Redelivery(2, email, "email", null, null)));
        Map<Integer, Map<String, String>> mdc = new HashMap<>();
        doAnswer(invocation -> {
            mdc.put(invocation.getArgument(0), MDC.getCopyOfContextMap());
            return null;
        }).when(emailService).redeliver(anyInt(), any(), any());

        job.retryDueEmails();

        assertThat(mdc.get(1)).containsEntry("requestId", "queued-req").containsEntry("traceId", trace);
        assertThat(mdc.get(1).get("spanId")).matches("[0-9a-f]{16}").isNotEqualTo("00f067aa0ba902b7");
        assertThat(mdc.get(2).get("requestId")).hasSize(36);
        assertThat(mdc.get(2).get("traceId")).matches("[0-9a-f]{32}").isNotEqualTo(trace);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void doesNothingWhenDisabled() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.retryDueEmails();

        verify(emailEventService, never()).claimDue(anyInt());
        verifyNoInteractions(emailService);
    }
}
