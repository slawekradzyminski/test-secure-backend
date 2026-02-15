package com.awesome.testing.config;

import com.awesome.testing.config.properties.PasswordResetProperties;
import com.awesome.testing.jms.JsonTextMessageConverter;
import com.awesome.testing.service.password.LocalEmailOutbox;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@SuppressWarnings("unused")
@Slf4j
@Configuration
@Profile("local")
public class LocalConfig {

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        return new JsonTextMessageConverter();
    }

    @Bean
    public JmsTemplate jmsTemplate(MessageConverter messageConverter,
                                   LocalEmailOutbox localEmailOutbox,
                                   PasswordResetProperties passwordResetProperties) {
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
                if (passwordResetProperties.isLocalOutboxEnabled() && message instanceof com.awesome.testing.dto.email.EmailDto emailDto) {
                    localEmailOutbox.store(destinationName, emailDto);
                }
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
