package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class TrafficPublisher {

    private final ConcurrentLinkedQueue<TrafficEventDto> queue;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelay = 500)
    public void broadcastTraffic() {
        while (!queue.isEmpty()) {
            TrafficEventDto event = queue.poll();
            messagingTemplate.convertAndSend("/topic/traffic", event);
        }
    }
} 