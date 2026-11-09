package com.awesome.testing.service.email;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailRetryJobTest {

    @Mock private EmailEventService emailEventService;
    @Mock private EmailService emailService;
    @InjectMocks private EmailRetryJob job;

    @Test
    void redeliversEveryClaimedEmail() {
        ReflectionTestUtils.setField(job, "enabled", true);
        EmailDto email = EmailDto.builder().to("a@example.com").subject("s").message("m").build();
        when(emailEventService.claimDue(EmailRetryJob.BATCH_SIZE)).thenReturn(List.of(
                new EmailEventService.Redelivery(1, email, "email"),
                new EmailEventService.Redelivery(2, email, "email")));

        job.retryDueEmails();

        verify(emailService).redeliver(1, email, "email");
        verify(emailService).redeliver(2, email, "email");
    }

    @Test
    void doesNothingWhenDisabled() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.retryDueEmails();

        verify(emailEventService, never()).claimDue(anyInt());
        verifyNoInteractions(emailService);
    }
}
