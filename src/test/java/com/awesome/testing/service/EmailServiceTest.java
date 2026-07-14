package com.awesome.testing.service;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.service.delay.DelayGenerator;
import com.awesome.testing.service.email.EmailEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private DelayGenerator delayGenerator;

    @Mock
    private EmailEventService emailEventService;

    @Mock
    private TaskScheduler emailTaskScheduler;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        when(delayGenerator.getDelayMillis()).thenReturn(0L);
        lenient().when(emailTaskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(0).run();
                    return null;
                });
    }

    @Test
    void shouldSendEmailAsynchronously() {
        EmailDto dto = EmailDto.builder()
                .to("test@example.com")
                .subject("Subject")
                .message("Body")
                .build();

        emailService.sendEmail(dto, "queue");

        verify(jmsTemplate, timeout(500)).convertAndSend("queue", dto);
        verify(emailEventService, never()).recordQueued(any(), any());
    }

    @Test
    void shouldTrackUserOwnedEmailEvent() {
        EmailDto dto = EmailDto.builder()
                .to("test@example.com")
                .subject("Subject")
                .message("Body")
                .build();
        UserEntity user = UserEntity.builder()
                .username("client")
                .email("test@example.com")
                .password("secret")
                .build();
        when(emailEventService.recordQueued(user, dto)).thenReturn(42);

        emailService.sendEmail(dto, "queue", user);

        verify(jmsTemplate, timeout(500)).convertAndSend("queue", dto);
        verify(emailEventService).recordQueued(user, dto);
        verify(emailEventService, timeout(500)).markSentToSink(eq(42));
    }

    @Test
    void shouldMarkUserOwnedEmailFailedWhenSchedulingIsRejected() {
        EmailDto dto = EmailDto.builder()
                .to("test@example.com")
                .subject("Subject")
                .message("Body")
                .build();
        UserEntity user = UserEntity.builder()
                .username("client")
                .email("test@example.com")
                .password("secret")
                .build();
        when(emailEventService.recordQueued(user, dto)).thenReturn(42);
        when(emailTaskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenThrow(new IllegalStateException("scheduler stopped"));

        emailService.sendEmail(dto, "queue", user);

        verify(emailEventService).markFailed(42, "scheduler stopped");
        verify(jmsTemplate, never()).convertAndSend(any(String.class), any(EmailDto.class));
    }
}
