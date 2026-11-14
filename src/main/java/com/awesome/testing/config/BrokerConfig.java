package com.awesome.testing.config;

import jakarta.persistence.EntityManagerFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Artemis connection factory and transaction managers for every runtime profile. Creating the
 * factory does not connect, so profiles that use the local email outbox never reach a broker.
 * Unit tests use Spring Boot's embedded broker instead.
 */
@SuppressWarnings("unused")
@Configuration
@Profile("!test")
public class BrokerConfig {

    @Value("${spring.artemis.broker-url:tcp://localhost:61616}")
    private String brokerUrl;

    @Value("${spring.artemis.user:admin}")
    private String username;

    @Value("${spring.artemis.password:admin}")
    private String password;

    @Bean
    public ActiveMQConnectionFactory connectionFactory() {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        connectionFactory.setUser(username);
        connectionFactory.setPassword(password);
        return connectionFactory;
    }

    @Bean(name = "jpaTransactionManager")
    public PlatformTransactionManager jpaTransactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean(name = "jmsTransactionManager")
    public PlatformTransactionManager jmsTransactionManager() {
        return new JmsTransactionManager(connectionFactory());
    }

    /**
     * The JPA transaction manager under the default name. It receives the bean instead of calling
     * {@link #jpaTransactionManager(EntityManagerFactory)}: in the native image a proxied {@code @Bean}
     * call with arguments cannot create the bean, which fails startup as soon as a bean injects
     * {@link PlatformTransactionManager} before {@code jpaTransactionManager} exists (3.22.0: the
     * checkout saga's {@code TransactionTemplate}s).
     */
    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(
            @Qualifier("jpaTransactionManager") PlatformTransactionManager jpaTransactionManager) {
        return jpaTransactionManager;
    }
}
