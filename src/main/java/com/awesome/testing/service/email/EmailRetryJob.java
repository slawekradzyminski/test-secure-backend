package com.awesome.testing.service.email;

import com.awesome.testing.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sends tracked emails again when the first send failed (for example because Artemis was
 * down) or never happened (the backend restarted during the training delay). Backoff and
 * the attempt limit live in {@link EmailEventService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailRetryJob {

    static final int BATCH_SIZE = 20;

    private final EmailEventService emailEventService;
    private final EmailService emailService;

    @Value("${app.email.retry.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.email.retry.interval:PT1M}",
            initialDelayString = "${app.email.retry.initial-delay:PT1M}")
    public void retryDueEmails() {
        if (!enabled) {
            return;
        }
        List<EmailEventService.Redelivery> due = emailEventService.claimDue(BATCH_SIZE);
        if (!due.isEmpty()) {
            log.info("Retrying {} email(s)", due.size());
        }
        due.forEach(redelivery -> emailService.redeliver(redelivery.eventId(), redelivery.email(), redelivery.destination()));
    }
}
