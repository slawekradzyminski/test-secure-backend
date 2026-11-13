package com.awesome.testing.traffic;

import static com.awesome.testing.factory.ollama.TrafficEventFactory.trafficEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.awesome.testing.dto.traffic.TrafficEventDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

@ExtendWith(MockitoExtension.class)
class TrafficPublisherTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private TrafficSessionOwners sessionOwners;

    private ConcurrentLinkedQueue<TrafficEventDto> queue;
    private TrafficPublisher trafficPublisher;

    @BeforeEach
    void setUp() {
        queue = new ConcurrentLinkedQueue<>();
        trafficPublisher = new TrafficPublisher(queue, messagingTemplate, new TrafficProperties(), sessionOwners);
    }

    @Test
    void shouldBroadcastEventsFromQueue() {
        // given
        TrafficEventDto event = trafficEvent();
        event.setOwnerUsername("session-user");
        queue.add(event);

        // when
        trafficPublisher.broadcastTraffic();

        // then
        verify(messagingTemplate).convertAndSend("/topic/traffic/test-session-1234", event);
    }

    @Test
    void deliveryFailureDoesNotPreventTheNextEventFromBeingSent() {
        var failed = trafficEvent();
        failed.setOwnerUsername("session-user");
        var next = trafficEvent();
        next.setOwnerUsername("session-user");
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
        trafficPublisher = new TrafficPublisher(queue, messagingTemplate, properties, sessionOwners);
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
    void legacyPublicAccessFeedsTheGlobalTopicAsBefore319() {
        // given
        TrafficProperties properties = new TrafficProperties();
        properties.setLegacyPublicAccess(true);
        trafficPublisher = new TrafficPublisher(queue, messagingTemplate, properties, sessionOwners);
        TrafficEventDto event = trafficEvent();
        event.setOwnerUsername("session-user");
        queue.add(event);

        // when
        trafficPublisher.broadcastTraffic();

        // then
        verify(messagingTemplate).convertAndSend("/topic/traffic", event);
        verify(messagingTemplate).convertAndSend("/topic/traffic/test-session-1234", event);
    }

    @Test
    void withoutLegacyPublicAccessNothingGoesToTheGlobalTopic() {
        // given
        TrafficEventDto event = trafficEvent();
        event.setOwnerUsername("session-user");
        queue.add(event);

        // when
        trafficPublisher.broadcastTraffic();

        // then
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/traffic"), any(Object.class));
    }

    @Test
    void sendsAnotherUsersEventOnABoundSessionToNobody() {
        // given
        when(sessionOwners.owner("test-session-1234")).thenReturn(Optional.of("session-user"));
        TrafficEventDto event = trafficEvent();
        event.setOwnerUsername("someone-else");
        queue.add(event);

        // when
        trafficPublisher.broadcastTraffic();

        // then
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void sendsAnonymousEventsOnlyToABoundSession() {
        // given
        TrafficEventDto unbound = trafficEvent();
        TrafficEventDto bound = trafficEvent();
        bound.setClientSessionId("bound-session-1234");
        when(sessionOwners.owner("test-session-1234")).thenReturn(Optional.empty());
        when(sessionOwners.owner("bound-session-1234")).thenReturn(Optional.of("session-user"));
        queue.add(unbound);
        queue.add(bound);

        // when
        trafficPublisher.broadcastTraffic();

        // then
        verify(messagingTemplate).convertAndSend("/topic/traffic/bound-session-1234", bound);
        verify(messagingTemplate, never()).convertAndSend("/topic/traffic/test-session-1234", unbound);
    }

    @Test
    void sendsTheOwnEventOfTheSessionUser() {
        // given
        when(sessionOwners.owner("test-session-1234")).thenReturn(Optional.of("session-user"));
        TrafficEventDto event = trafficEvent();
        event.setOwnerUsername("session-user");
        queue.add(event);

        // when
        trafficPublisher.broadcastTraffic();

        // then
        verify(messagingTemplate).convertAndSend("/topic/traffic/test-session-1234", event);
    }
}
