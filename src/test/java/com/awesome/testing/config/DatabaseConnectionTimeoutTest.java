package com.awesome.testing.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A refused database must not cost every request HikariCP's default 30 s connection timeout
 * (exploratory chaos session, finding F3): requests give up after a few seconds and answer 503.
 */
class DatabaseConnectionTimeoutTest {

    @Test
    void requestsWaitAFewSecondsForAPooledConnection() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        Object timeout = sources.getFirst().getProperty("spring.datasource.hikari.connection-timeout");

        assertThat(timeout).isNotNull();
        assertThat(Duration.ofMillis(Long.parseLong(timeout.toString()))).isLessThanOrEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void openingAConnectionOverASlowLinkKeepsItsOwnLongerLimit() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        assertThat(sources.getFirst().getProperty("spring.datasource.hikari.data-source-properties.loginTimeout"))
                .hasToString("30");
    }
}
