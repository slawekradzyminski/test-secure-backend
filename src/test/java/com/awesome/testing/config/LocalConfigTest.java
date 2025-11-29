package com.awesome.testing.config;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class LocalConfigTest {

    private LocalConfig config;

    @BeforeEach
    void setUp() {
        config = new LocalConfig();
    }

    @Test
    void shouldCreateJacksonJmsMessageConverter() {
        MessageConverter converter = config.jacksonJmsMessageConverter();

        assertThat(converter).isInstanceOf(MappingJackson2MessageConverter.class);
    }

    @Test
    void shouldCreateStubJmsTemplate() {
        MessageConverter converter = new MappingJackson2MessageConverter();

        var jmsTemplate = config.jmsTemplate(converter);

        assertThat(jmsTemplate.getMessageConverter()).isEqualTo(converter);
        // the stub overrides afterPropertiesSet(), so it should not fail without a ConnectionFactory
        assertThatCode(jmsTemplate::afterPropertiesSet).doesNotThrowAnyException();
        assertThatCode(() -> jmsTemplate.convertAndSend("queue", "payload")).doesNotThrowAnyException();
    }

    @Test
    void shouldCreateJpaTransactionManagers() {
        EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);

        PlatformTransactionManager jpaTm = config.jpaTransactionManager(entityManagerFactory);
        PlatformTransactionManager transactionManager = config.transactionManager(entityManagerFactory);

        assertThat(jpaTm).isInstanceOf(JpaTransactionManager.class);
        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
    }
}
