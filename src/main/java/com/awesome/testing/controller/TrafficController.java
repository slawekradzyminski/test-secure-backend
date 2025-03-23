package com.awesome.testing.controller;

import com.awesome.testing.dto.traffic.TrafficInfoDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/traffic")
@Tag(name = "Traffic Monitoring", description = "Endpoints for HTTP traffic monitoring")
public class TrafficController {

    @GetMapping("/info")
    @Operation(
            summary = "Get traffic monitoring information",
            description = "Returns information about WebSocket endpoints for traffic monitoring",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully returned info"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public TrafficInfoDto getTrafficInfo() {
        return TrafficInfoDto.builder()
                .webSocketEndpoint("/ws-traffic")
                .topic("/topic/traffic")
                .description("Connect to the WebSocket endpoint and subscribe to the topic to receive real-time HTTP traffic events")
                .build();
    }
} 