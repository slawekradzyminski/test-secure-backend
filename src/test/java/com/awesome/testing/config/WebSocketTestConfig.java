package com.awesome.testing.config;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.traffic.TrafficPublisher;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.concurrent.ConcurrentLinkedQueue;

@TestConfiguration
@Profile("websocket-test")
public class WebSocketTestConfig {

    @Bean
    public ConcurrentLinkedQueue<TrafficEventDto> trafficQueue() {
        return new ConcurrentLinkedQueue<>();
    }
    
    @Bean
    @Primary
    public SimpMessagingTemplate simpMessagingTemplate() {
        return Mockito.mock(SimpMessagingTemplate.class);
    }
    
    @Bean
    @Primary
    public TrafficPublisher trafficPublisher(
            ConcurrentLinkedQueue<TrafficEventDto> trafficQueue,
            SimpMessagingTemplate messagingTemplate) {
        return new TrafficPublisher(trafficQueue, messagingTemplate);
    }
} 