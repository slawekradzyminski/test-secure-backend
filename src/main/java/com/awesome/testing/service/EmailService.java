package com.awesome.testing.service;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.service.delay.DelayGenerator;

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

    public void sendEmail(EmailDto emailDTO, String destination) {
        Thread.ofVirtual().start(() -> {
            try {
                long delay = delayGenerator.getDelayMillis();
                logDelay(delay);
                Thread.sleep(delay);
                jmsTemplate.convertAndSend(destination, emailDTO);
                log.info("Email sent to {}", emailDTO.getTo());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Email sending was interrupted", e);
            }
        });
    }

    private void logDelay(long delay) {
        long minutes = delay / 60000;
        long seconds = (delay % 60000) / 1000;
        log.info("Delaying email send by {} minutes and {} seconds", minutes, seconds);
    }
}