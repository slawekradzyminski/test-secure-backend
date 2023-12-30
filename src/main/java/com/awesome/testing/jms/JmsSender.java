package com.awesome.testing.jms;

import com.awesome.testing.dto.email.EmailDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class JmsSender {

    private final JmsTemplate jmsTemplate;
    private final DelayProvider delayProvider;

    @Async
    public void asyncSendTo(String destination, EmailDto email) {
        long delay = delayProvider.getRandomDelayInSeconds();
        log.info("Will wait {} seconds before sending {}", delay, email);

        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(delay * 1000);
                jmsTemplate.convertAndSend(destination, email);
                log.info("Message {} sent successfully", email);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Error while sleeping", e);
            }
        });
    }

}
