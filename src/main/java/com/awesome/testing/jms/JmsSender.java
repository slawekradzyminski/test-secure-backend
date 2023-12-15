package com.awesome.testing.jms;

import com.awesome.testing.dto.email.EmailDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JmsSender {

    @Autowired
    private JmsTemplate jmsTemplate;

    @Async
    public void asyncSendTo(String destination, EmailDTO email) {
        jmsTemplate.convertAndSend(destination, email);
        log.info("Message {} sent successfully", email);
    }

}
