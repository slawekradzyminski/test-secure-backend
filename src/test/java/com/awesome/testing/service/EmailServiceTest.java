package com.awesome.testing.service;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.service.delay.DelayGenerator;
import com.awesome.testing.service.email.EmailEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.awesome.testing.correlation.CorrelationTracing;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Spy
    private CorrelationTracing correlationTracing = CorrelationTracing.disabled();

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        lenient().when(delayGenerator.getDelayMillis()).thenReturn(0L);
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
        verify(emailEventService, never()).recordQueued(any(), any(), any(), any());
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
        when(emailEventService.recordQueued(user, dto, "queue", null)).thenReturn(42);

        emailService.sendEmail(dto, "queue", user);

        verify(jmsTemplate, timeout(500)).convertAndSend("queue", dto);
        verify(emailEventService).recordQueued(user, dto, "queue", null);
        verify(emailEventService, timeout(500)).markSentToSink(eq(42));
    }

    @Test
    void shouldNotSendWhenTheUserWasDeletedBeforeTheEventWasRecorded() {
        EmailDto dto = EmailDto.builder().to("gone@example.com").subject("Subject").message("Body").build();
        UserEntity user = UserEntity.builder().username("gone").email("gone@example.com").password("secret").build();
        when(emailEventService.recordQueued(user, dto, "queue", null)).thenReturn(null);

        emailService.sendEmail(dto, "queue", user);

        verify(emailTaskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        verify(jmsTemplate, never()).convertAndSend(any(String.class), any(Object.class));
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
        when(emailEventService.recordQueued(user, dto, "queue", null)).thenReturn(42);
        when(emailTaskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenThrow(new IllegalStateException("scheduler stopped"));

        emailService.sendEmail(dto, "queue", user);

        verify(emailEventService).markFailed(42, "scheduler stopped");
        verify(jmsTemplate, never()).convertAndSend(any(String.class), any(EmailDto.class));
    }

    @Test
    void shouldMarkTheEventFailedWhenTheBrokerRejectsTheEmail() {
        EmailDto dto = EmailDto.builder().to("test@example.com").subject("Subject").message("Body").build();
        UserEntity user = UserEntity.builder().username("client").email("test@example.com").password("secret").build();
        java.util.UUID requestId = java.util.UUID.randomUUID();
        when(emailEventService.recordQueued(user, dto, "queue", requestId)).thenReturn(7);
        org.mockito.Mockito.doThrow(new org.springframework.jms.UncategorizedJmsException("broker down"))
                .when(jmsTemplate).convertAndSend("queue", dto);

        emailService.sendEmail(dto, "queue", user, requestId);

        verify(emailEventService).markFailed(7, "broker down");
        verify(emailEventService, never()).markSentToSink(any());
    }

    @Test
    void theDelayedSendRunsUnderTheIdsOfTheQueuingRequest() {
        EmailDto dto = EmailDto.builder().to("test@example.com").subject("Subject").message("Body").build();
        java.util.concurrent.atomic.AtomicReference<Runnable> scheduled = new java.util.concurrent.atomic.AtomicReference<>();
        when(emailTaskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            scheduled.set(invocation.getArgument(0));
            return null;
        });
        java.util.Map<String, String> mdcWhileSending = new java.util.HashMap<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            mdcWhileSending.putAll(org.slf4j.MDC.getCopyOfContextMap());
            return null;
        }).when(jmsTemplate).convertAndSend("queue", dto);
        String trace = "4bf92f3577b34da6a3ce929d0e0e4736";

        try (var ignored = new com.awesome.testing.correlation.CorrelationIds("email-req", trace, "00f067aa0ba902b7").open()) {
            emailService.sendEmail(dto, "queue");
        }
        scheduled.get().run();

        assertThat(mdcWhileSending).containsEntry("requestId", "email-req").containsEntry("traceId", trace);
        assertThat(mdcWhileSending.get("spanId")).isNotEqualTo("00f067aa0ba902b7");
        assertThat(org.slf4j.MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void shouldRedeliverImmediatelyWithoutTheTrainingDelay() {
        EmailDto dto = EmailDto.builder().to("test@example.com").subject("Subject").message("Body").build();
        org.mockito.Mockito.reset(delayGenerator);

        emailService.redeliver(9, dto, "queue");

        verify(jmsTemplate).convertAndSend("queue", dto);
        verify(emailEventService).markSentToSink(9);
        verify(emailTaskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        org.mockito.Mockito.verifyNoInteractions(delayGenerator);
    }
}
