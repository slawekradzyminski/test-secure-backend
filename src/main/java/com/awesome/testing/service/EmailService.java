package com.awesome.testing.service;

import com.awesome.testing.dto.EmailDTO;
import com.awesome.testing.jms.JmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JmsSender jmsSender;

    @Value("${activemq.destination}")
    private String destination;

    public void sendEmail(EmailDTO email) {
        jmsSender.asyncSendTo(destination, email);
    }

} 