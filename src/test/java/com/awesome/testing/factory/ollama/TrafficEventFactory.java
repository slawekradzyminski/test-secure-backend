package com.awesome.testing.factory.ollama;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import lombok.experimental.UtilityClass;

import java.time.Instant;

@UtilityClass
public class TrafficEventFactory {

    public static TrafficEventDto trafficEvent() {
        return TrafficEventDto.builder()
                .method("GET")
                .path("/api/test")
                .status(200)
                .durationMs(100)
                .timestamp(Instant.now())
                .build();
    }

}
