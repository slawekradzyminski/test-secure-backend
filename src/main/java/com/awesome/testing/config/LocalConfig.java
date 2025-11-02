package com.awesome.testing.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.jms.JmsException;

@SuppressWarnings("unused")
@Slf4j
@Configuration
@Profile("local")
public class LocalConfig {

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_awesome_");
        return converter;
    }

    @Bean
    public JmsTemplate jmsTemplate(MessageConverter messageConverter) {
        return new JmsTemplate() {
            {
                setMessageConverter(messageConverter);
            }

            @Override
            public void afterPropertiesSet() {
                // Skip ConnectionFactory requirement for the local stub
            }

            @Override
            public void convertAndSend(String destinationName, Object message) throws JmsException {
                log.info("Local JMS stub - skipping send to {} with payload {}", destinationName, message);
            }
        };
    }

    @Bean(name = "jpaTransactionManager")
    public PlatformTransactionManager jpaTransactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return jpaTransactionManager(entityManagerFactory);
    }
}
