package com.awesome.testing.config;

import jakarta.persistence.EntityManagerFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import lombok.SneakyThrows;

class BrokerConfigTest {

    private BrokerConfig config;

    @BeforeEach
    void setUp() {
        config = new BrokerConfig();
        ReflectionTestUtils.setField(config, "brokerUrl", "tcp://localhost:61616");
        ReflectionTestUtils.setField(config, "username", "admin");
        ReflectionTestUtils.setField(config, "password", "secret");
    }

    @SneakyThrows
    @Test
    void shouldCreateConnectionFactoryWithCredentials() {
        ActiveMQConnectionFactory connectionFactory = config.connectionFactory();

        assertThat(connectionFactory.toURI().toString()).startsWith("tcp://localhost:61616");
        assertThat(connectionFactory.getUser()).isEqualTo("admin");
        assertThat(connectionFactory.getPassword()).isEqualTo("secret");
    }

    @Test
    void shouldCreateJpaTransactionManagerAndJmsTransactionManager() {
        EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
        PlatformTransactionManager jpaTransactionManager = config.jpaTransactionManager(entityManagerFactory);

        assertThat(jpaTransactionManager).isInstanceOf(JpaTransactionManager.class);
        assertThat(config.jmsTransactionManager()).isInstanceOf(JmsTransactionManager.class);
        // The primary manager is the JPA one itself, not a second instance built by a proxied call.
        assertThat(config.transactionManager(jpaTransactionManager)).isSameAs(jpaTransactionManager);
    }
}
