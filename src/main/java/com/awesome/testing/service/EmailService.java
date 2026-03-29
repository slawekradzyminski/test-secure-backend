package com.awesome.testing.service;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.service.delay.DelayGenerator;
import com.awesome.testing.service.email.EmailEventService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JmsTemplate jmsTemplate;
    private final DelayGenerator delayGenerator;
    private final EmailEventService emailEventService;

    public void sendEmail(EmailDto emailDto, String destination) {
        sendEmail(emailDto, destination, null);
    }

    public void sendEmail(EmailDto emailDto, String destination, UserEntity user) {
        Integer emailEventId = user == null ? null : emailEventService.recordQueued(user, emailDto);
        Thread.ofVirtual().start(() -> {
            try {
                long delay = delayGenerator.getDelayMillis();
                logDelay(delay);
                Thread.sleep(delay);
                jmsTemplate.convertAndSend(destination, emailDto);
                markSent(emailEventId);
                log.info("Email sent to {}", emailDto.getTo());
            } catch (RuntimeException e) {
                markFailed(emailEventId, e);
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                markFailed(emailEventId, e);
                log.error("Email sending was interrupted", e);
            }
        });
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
