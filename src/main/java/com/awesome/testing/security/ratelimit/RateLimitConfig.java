package com.awesome.testing.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    @Primary
    Clock rateLimitClock() {
        // PostgreSQL keeps microseconds; ticking in micros keeps returned and reloaded timestamps equal on Linux.
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
