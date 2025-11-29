package com.awesome.testing.service;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.service.delay.DelayGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private DelayGenerator delayGenerator;

    @InjectMocks
    private EmailService emailService;

    @Test
    void shouldSendEmailAsynchronously() {
        EmailDto dto = EmailDto.builder()
                .to("test@example.com")
                .subject("Subject")
                .message("Body")
                .build();
        when(delayGenerator.getDelayMillis()).thenReturn(0L);

        emailService.sendEmail(dto, "queue");

        verify(jmsTemplate, timeout(500)).convertAndSend("queue", dto);
    }
}
