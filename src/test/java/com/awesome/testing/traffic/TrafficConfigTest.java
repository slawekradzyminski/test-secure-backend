package com.awesome.testing.traffic;

import com.awesome.testing.config.TestConfig;
import com.awesome.testing.dto.traffic.TrafficEventDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Import(TestConfig.class)
@ActiveProfiles("test")
class TrafficConfigTest {

    @Autowired
    private ConcurrentLinkedQueue<TrafficEventDto> trafficQueue;

    @MockitoBean
    @SuppressWarnings("unused")
    private TrafficPublisher trafficPublisher;

    @Test
    void shouldCreateTrafficQueueBean() {
        assertThat(trafficQueue).isNotNull();
        assertThat(trafficQueue).isEmpty();
    }

    @Test
    void shouldAllowAddingEventsToQueue() {
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
