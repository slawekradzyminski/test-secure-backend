package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Queue;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class TrafficPublisher {

    private final Queue<TrafficEventDto> queue;
    private final SimpMessagingTemplate messagingTemplate;
    private final TrafficProperties trafficProperties;

    @Scheduled(fixedDelay = 500)
    public void broadcastTraffic() {
        for (int sent = 0; sent < trafficProperties.getEventQueueCapacity(); sent++) {
            TrafficEventDto event = queue.poll();
            if (event == null) {
                return;
            }
            try {
                if (trafficProperties.isLegacyPublicAccess()) {
                    messagingTemplate.convertAndSend("/topic/traffic", event);
                }
                if (event.getClientSessionId() != null) {
                    messagingTemplate.convertAndSend(TrafficSession.topic(event.getClientSessionId()), event);
                }
            } catch (RuntimeException exception) {
                log.warn("Traffic event delivery failed");
            }
        }
    }
}
