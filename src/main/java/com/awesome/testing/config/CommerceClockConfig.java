package com.awesome.testing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CommerceClockConfig {

    /**
     * Server-local clock used by coupon validity, return windows, and
     * fulfilment timestamps, matching the server-local {@code LocalDateTime}
     * values exposed by the REST API.
     */
    @Bean
    Clock commerceClock() {
        return Clock.systemDefaultZone();
    }
}
