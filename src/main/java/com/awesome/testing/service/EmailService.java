package com.awesome.testing.service;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.service.delay.DelayGenerator;
import com.awesome.testing.service.email.EmailEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

/**
 * Publishes emails to the JMS queue (or the local outbox) after a random training delay.
 * Emails for a known user are tracked as email events that store the payload, so
 * {@link com.awesome.testing.service.email.EmailRetryJob} can retry failed or lost sends.
 * The backend renders every subject and body; the consumer only delivers them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JmsTemplate jmsTemplate;
    private final DelayGenerator delayGenerator;
    private final EmailEventService emailEventService;
    @Qualifier("emailTaskScheduler")
    private final TaskScheduler emailTaskScheduler;

    public void sendEmail(EmailDto emailDto, String destination) {
        sendEmail(emailDto, destination, null, null);
    }

    public void sendEmail(EmailDto emailDto, String destination, UserEntity user) {
        sendEmail(emailDto, destination, user, null);
    }

    /**
     * @param requestId optional idempotency key of the caller; an email event with the same
     *                  key is never recorded twice
     */
    public void sendEmail(EmailDto emailDto, String destination, UserEntity user, UUID requestId) {
        Integer emailEventId = user == null ? null : emailEventService.recordQueued(user, emailDto, destination, requestId);
        long delay = delayGenerator.getDelayMillis();
        logDelay(delay);
        try {
            emailTaskScheduler.schedule(() -> deliver(emailEventId, emailDto, destination), Instant.now().plusMillis(delay));
        } catch (RuntimeException e) {
            markFailed(emailEventId, e);
            log.error("Email scheduling failed for {}", emailDto.getTo(), e);
        }
    }

    /** Sends a tracked email again, immediately; used by the retry job. */
    public void redeliver(Integer emailEventId, EmailDto emailDto, String destination) {
        deliver(emailEventId, emailDto, destination);
    }

    private void deliver(Integer emailEventId, EmailDto emailDto, String destination) {
        try {
            jmsTemplate.convertAndSend(destination, emailDto);
            markSent(emailEventId);
            log.info("Email sent to {}", emailDto.getTo());
        } catch (RuntimeException e) {
            markFailed(emailEventId, e);
            log.error("Email sending failed for {}", emailDto.getTo(), e);
        }
    }

    private void markSent(Integer emailEventId) {
        if (emailEventId != null) {
            emailEventService.markSentToSink(emailEventId);
        }
    }

    private void markFailed(Integer emailEventId, Exception e) {
        if (emailEventId != null) {
            emailEventService.markFailed(emailEventId, e.getMessage());
        }
    }

    private void logDelay(long delay) {
        long minutes = delay / 60_000;
        long seconds = delay % 60_000 / 1000;
        log.info("Delaying email send by {} minutes and {} seconds", minutes, seconds);
    }
}
