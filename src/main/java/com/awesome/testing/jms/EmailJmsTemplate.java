package com.awesome.testing.jms;

import com.awesome.testing.config.properties.PasswordResetProperties;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.service.password.LocalEmailOutbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;

/**
 * Sends emails through Artemis, or captures them in the local outbox without touching a broker.
 */
@Slf4j
public class EmailJmsTemplate extends JmsTemplate {

    private final EmailTransport transport;
    private final LocalEmailOutbox localEmailOutbox;
    private final PasswordResetProperties passwordResetProperties;

    public EmailJmsTemplate(EmailTransport transport,
                            LocalEmailOutbox localEmailOutbox,
                            PasswordResetProperties passwordResetProperties) {
        super();
        this.transport = transport;
        this.localEmailOutbox = localEmailOutbox;
        this.passwordResetProperties = passwordResetProperties;
    }

    @Override
    public void convertAndSend(String destinationName, Object message) throws JmsException {
        if (transport == EmailTransport.JMS) {
            super.convertAndSend(destinationName, message);
            return;
        }
        log.info("Local email outbox - not sending to {} with payload {}", destinationName, message);
        if (passwordResetProperties.isLocalOutboxEnabled() && message instanceof EmailDto emailDto) {
            localEmailOutbox.store(destinationName, emailDto);
        }
    }
}
