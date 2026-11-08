package com.awesome.testing.jms;

import com.awesome.testing.config.properties.PasswordResetProperties;
import com.awesome.testing.service.password.LocalEmailOutbox;
import jakarta.jms.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsMessageOperations;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;
import tools.jackson.databind.ObjectMapper;

/**
 * Email transport for every profile. {@code app.email.transport} selects, at runtime, between
 * Artemis ({@code jms}) and the in-memory local outbox ({@code local-outbox}); both paths are
 * always wired so the bean graph does not depend on the active profile.
 */
@SuppressWarnings("unused")
@Configuration
@EnableJms
public class EmailConfig {

    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        return new JsonTextMessageConverter(objectMapper);
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory,
                                   MessageConverter messageConverter,
                                   LocalEmailOutbox localEmailOutbox,
                                   PasswordResetProperties passwordResetProperties,
                                   @Value("${app.email.transport:jms}") String transport) {
        EmailJmsTemplate jmsTemplate = new EmailJmsTemplate(
                EmailTransport.from(transport), localEmailOutbox, passwordResetProperties);
        jmsTemplate.setConnectionFactory(connectionFactory);
        jmsTemplate.setMessageConverter(messageConverter);
        jmsTemplate.setSessionTransacted(true);
        return jmsTemplate;
    }

    @Bean
    public JmsMessageOperations jmsMessageOperations(JmsTemplate jmsTemplate, MessageConverter messageConverter) {
        JmsMessagingTemplate jmsMessagingTemplate = new JmsMessagingTemplate();
        jmsMessagingTemplate.setJmsTemplate(jmsTemplate);
        jmsMessagingTemplate.setJmsMessageConverter(messageConverter);
        jmsMessagingTemplate.afterPropertiesSet();
        return jmsMessagingTemplate;
    }
}
