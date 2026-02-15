package com.awesome.testing.jms;

import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsMessageOperations;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.annotation.EnableJms;

@SuppressWarnings("unused")
@Configuration
@EnableJms
@Profile("!local")
public class EmailConfig {

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
        jmsTemplate.setMessageConverter(messageConverter);
        jmsTemplate.setSessionTransacted(true);
        return jmsTemplate;
    }

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        return new JsonTextMessageConverter();
    }

    @Bean
    public JmsMessageOperations jmsMessageOperations(JmsTemplate jmsTemplate, MessageConverter messageConverter) {
        if (jmsTemplate.getMessageConverter() == null) {
            jmsTemplate.setMessageConverter(messageConverter);
        }
        JmsMessagingTemplate jmsMessagingTemplate = new JmsMessagingTemplate();
        jmsMessagingTemplate.setJmsTemplate(jmsTemplate);
        jmsMessagingTemplate.setJmsMessageConverter(messageConverter);
        jmsMessagingTemplate.afterPropertiesSet();
        return jmsMessagingTemplate;
    }

}
