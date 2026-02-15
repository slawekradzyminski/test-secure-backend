package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import org.junit.jupiter.api.Test;

import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficConfigTest {

    @Test
    void shouldCreateTrafficQueueBean() {
        Queue<TrafficEventDto> trafficQueue = new TrafficConfig().trafficQueue();
        assertThat(trafficQueue).isNotNull();
        assertThat(trafficQueue).isEmpty();
    }

    @Test
    void shouldAllowAddingEventsToQueue() {
        Queue<TrafficEventDto> trafficQueue = new TrafficConfig().trafficQueue();
        TrafficEventDto event = TrafficEventDto.builder()
                .method("GET")
                .path("/api/test")
                .status(200)
                .build();

        trafficQueue.offer(event);

        assertThat(trafficQueue).hasSize(1);
        assertThat(trafficQueue.poll()).isEqualTo(event);
    }
}
