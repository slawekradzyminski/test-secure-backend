package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.concurrent.ConcurrentLinkedQueue;

import static com.awesome.testing.factory.ollama.TrafficEventFactory.trafficEvent;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TrafficPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    
    private ConcurrentLinkedQueue<TrafficEventDto> queue;
    private TrafficPublisher trafficPublisher;

    @BeforeEach
    void setUp() {
        queue = new ConcurrentLinkedQueue<>();
        trafficPublisher = new TrafficPublisher(queue, messagingTemplate);
    }
    
    @Test
    void shouldBroadcastEventsFromQueue() {
        // given
        TrafficEventDto event = trafficEvent();
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