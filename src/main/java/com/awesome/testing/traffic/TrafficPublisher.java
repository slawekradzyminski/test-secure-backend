package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Queue;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class TrafficPublisher {

    private final Queue<TrafficEventDto> queue;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelay = 500)
    public void broadcastTraffic() {
        while (!queue.isEmpty()) {
            TrafficEventDto event = queue.poll();
            messagingTemplate.convertAndSend("/topic/traffic", event);
        }
    }
} 
