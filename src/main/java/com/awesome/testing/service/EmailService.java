package com.awesome.testing.service;

import com.awesome.testing.dto.EmailDTO;
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

    public void sendEmail(EmailDTO emailDTO, String destination) {
        Thread.ofVirtual().start(() -> {
            try {
                long delay = delayGenerator.getDelayMillis();
                log.info("Delaying email send by {} ms", delay);
                Thread.sleep(delay);
                jmsTemplate.convertAndSend(destination, emailDTO);
                log.info("Email sent to {}", emailDTO.getRecipient());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Email sending was interrupted", e);
            }
        });
    }
} 