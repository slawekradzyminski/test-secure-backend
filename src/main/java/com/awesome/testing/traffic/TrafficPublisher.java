package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Queue;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class TrafficPublisher {

    private final Queue<TrafficEventDto> queue;
    private final SimpMessagingTemplate messagingTemplate;
    private final TrafficProperties trafficProperties;
    private final TrafficSessionOwners sessionOwners;

    @Scheduled(fixedDelay = 500)
    public void broadcastTraffic() {
        for (int sent = 0; sent < trafficProperties.getEventQueueCapacity(); sent++) {
            TrafficEventDto event = queue.poll();
            if (event == null) {
                return;
            }
            try {
                // Legacy public access no longer feeds the global /topic/traffic (catalog X-03): events go
                // only to the session topic, and only when they belong to the session's user.
                if (event.getClientSessionId() != null && belongsToSessionUser(event)) {
                    messagingTemplate.convertAndSend(TrafficSession.topic(event.getClientSessionId()), event);
                }
            } catch (RuntimeException exception) {
                log.warn("Traffic event delivery failed");
            }
        }
    }

    private boolean belongsToSessionUser(TrafficEventDto event) {
        Optional<String> sessionUser = sessionOwners.owner(event.getClientSessionId());
        String owner = event.getOwnerUsername();
        if (owner == null) {
            return sessionUser.isPresent();
        }
        return sessionUser.map(owner::equals).orElse(true);
    }
}
