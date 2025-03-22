package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.mockito.Mockito.verify;

class TrafficPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    
    private ConcurrentLinkedQueue<TrafficEventDto> queue;
    private TrafficPublisher trafficPublisher;

    @BeforeEach
    void setUp() {
        // given
        MockitoAnnotations.openMocks(this);
        queue = new ConcurrentLinkedQueue<>();
        trafficPublisher = new TrafficPublisher(queue, messagingTemplate);
    }
    
    @Test
    void shouldBroadcastEventsFromQueue() {
        // given
        TrafficEventDto event = TrafficEventDto.builder()
                .method("GET")
                .path("/api/test")
                .status(200)
                .durationMs(100)
                .timestamp(Instant.now())
                .build();
        
        queue.add(event);
        
        // when
        trafficPublisher.broadcastTraffic();
        
        // then
        verify(messagingTemplate).convertAndSend("/topic/traffic", event);
    }
    
    @Test
    void shouldHandleEmptyQueue() {
        // given - empty queue
        
        // when
        trafficPublisher.broadcastTraffic();
        
        // then - no exception thrown and no method calls on messagingTemplate
    }
} 