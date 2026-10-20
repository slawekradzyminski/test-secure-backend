package com.awesome.testing.traffic;

import static com.awesome.testing.factory.ollama.TrafficEventFactory.trafficEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.awesome.testing.dto.traffic.TrafficEventDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.concurrent.ConcurrentLinkedQueue;

@ExtendWith(MockitoExtension.class)
class TrafficPublisherTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    private ConcurrentLinkedQueue<TrafficEventDto> queue;
    private TrafficPublisher trafficPublisher;

    @BeforeEach
    void setUp() {
        queue = new ConcurrentLinkedQueue<>();
        trafficPublisher = new TrafficPublisher(queue, messagingTemplate, new TrafficProperties());
    }

    @Test
    void shouldBroadcastEventsFromQueue() {
        // given
        TrafficEventDto event = trafficEvent();
        queue.add(event);

        // when
        trafficPublisher.broadcastTraffic();

        // then
        verify(messagingTemplate).convertAndSend("/topic/traffic/test-session-1234", event);
    }

    @Test
    void deliveryFailureDoesNotPreventTheNextEventFromBeingSent() {
        var failed = trafficEvent();
        var next = trafficEvent();
        next.setPath("/next");
        queue.add(failed);
        queue.add(next);
        doThrow(new IllegalStateException("PRIVATE_BROKER_DETAILS"))
                .when(messagingTemplate)
                .convertAndSend("/topic/traffic/test-session-1234", failed);

        trafficPublisher.broadcastTraffic();

        verify(messagingTemplate).convertAndSend("/topic/traffic/test-session-1234", next);
        assertThat(queue).isEmpty();
    }

    @Test
    void eachTickHasABoundedWorkBudget() {
        var properties = new TrafficProperties();
        properties.setEventQueueCapacity(1);
        trafficPublisher = new TrafficPublisher(queue, messagingTemplate, properties);
        queue.add(trafficEvent());
        queue.add(trafficEvent());

        trafficPublisher.broadcastTraffic();

        assertThat(queue).hasSize(1);
    }

    @Test
    void shouldHandleEmptyQueue() {
        trafficPublisher.broadcastTraffic();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void shouldAlsoBroadcastToGlobalTopicWhenLegacyPublicAccessIsEnabled() {
        TrafficProperties properties = new TrafficProperties();
        properties.setLegacyPublicAccess(true);
        trafficPublisher = new TrafficPublisher(queue, messagingTemplate, properties);
        TrafficEventDto event = trafficEvent();
        queue.add(event);

        trafficPublisher.broadcastTraffic();

        verify(messagingTemplate).convertAndSend("/topic/traffic", event);
        verify(messagingTemplate).convertAndSend("/topic/traffic/test-session-1234", event);
    }
}
