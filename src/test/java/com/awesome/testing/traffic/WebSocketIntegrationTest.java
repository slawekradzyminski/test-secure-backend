package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.awesome.testing.factory.ollama.TrafficEventFactory.trafficEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketIntegrationTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ConcurrentLinkedQueue<TrafficEventDto> trafficQueue;
    private TrafficPublisher trafficPublisher;

    @BeforeEach
    void setUp() {
        trafficQueue = new ConcurrentLinkedQueue<>();
        trafficPublisher = new TrafficPublisher(trafficQueue, messagingTemplate);
    }

    @Test
    void shouldReceiveTrafficEventViaWebSocket() {
        TrafficEventDto testEvent = trafficEvent();
        trafficQueue.add(testEvent);

        trafficPublisher.broadcastTraffic();

        ArgumentCaptor<TrafficEventDto> eventCaptor = ArgumentCaptor.forClass(TrafficEventDto.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/traffic"), eventCaptor.capture());
        List<TrafficEventDto> capturedEvents = eventCaptor.getAllValues();
        TrafficEventDto capturedEvent = capturedEvents.getLast();
        assertThat(capturedEvent.getMethod()).isEqualTo(testEvent.getMethod());
        assertThat(capturedEvent.getPath()).isEqualTo(testEvent.getPath());
        assertThat(capturedEvent.getStatus()).isEqualTo(testEvent.getStatus());
        assertThat(capturedEvent.getDurationMs()).isEqualTo(testEvent.getDurationMs());
    }
}

