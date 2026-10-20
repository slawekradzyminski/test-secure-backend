package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Queue;

@Configuration
public class TrafficConfig {

    @SuppressWarnings("unused")
    @Bean
    public Queue<TrafficEventDto> trafficQueue(TrafficProperties properties, MeterRegistry metrics) {
        return new BoundedTrafficQueue(properties.getEventQueueCapacity(), metrics);
    }
}
