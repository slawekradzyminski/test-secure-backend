package com.awesome.testing.traffic;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/traffic")
@Tag(name = "Traffic Monitoring", description = "Endpoints for HTTP traffic monitoring")
public class TrafficController {

    @GetMapping("/info")
    @Operation(
            summary = "Get traffic monitoring information",
            description = "Returns information about WebSocket endpoints for traffic monitoring"
    )
    public Map<String, Object> getTrafficInfo() {
        return Map.of(
                "webSocketEndpoint", "/ws-traffic",
                "topic", "/topic/traffic",
                "description", "Connect to the WebSocket endpoint and subscribe to the topic to receive real-time HTTP traffic events"
        );
    }
} 