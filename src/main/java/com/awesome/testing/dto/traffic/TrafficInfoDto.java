package com.awesome.testing.dto.traffic;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Traffic info web websockets connection")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficInfoDto {

    @Schema(description = "Websocket endpoint", example = "/ws-traffic")
    private String webSocketEndpoint;

    @Schema(description = "Websocket topic", example = "/topic/traffic")
    private String topic;

    @Schema(description = "Websocket description", example = "Connect to the WebSocket endpoint and subscribe to the topic to receive real-time HTTP traffic events")
    private String description;

}
