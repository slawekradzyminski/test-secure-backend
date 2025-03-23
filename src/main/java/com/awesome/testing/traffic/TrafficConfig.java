package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentLinkedQueue;

@Configuration
public class TrafficConfig {

    @SuppressWarnings("unused")
    @Bean
    public ConcurrentLinkedQueue<TrafficEventDto> trafficQueue() {
        return new ConcurrentLinkedQueue<>();
    }
} 