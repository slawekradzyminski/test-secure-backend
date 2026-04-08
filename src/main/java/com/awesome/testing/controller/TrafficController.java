package com.awesome.testing.controller;

import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.traffic.TrafficInfoDto;
import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.traffic.TrafficLogService;
import com.awesome.testing.traffic.TrafficProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/traffic")
@Tag(name = "Traffic Monitoring", description = "Endpoints for HTTP traffic monitoring")
@RequiredArgsConstructor
public class TrafficController {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "timestamp");

    private final TrafficLogService trafficLogService;
    private final TrafficProperties trafficProperties;

    @GetMapping("/info")
    @Operation(
            summary = "Get traffic monitoring information",
            description = "Returns information about WebSocket endpoints for traffic monitoring"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully returned info")
    })
    public TrafficInfoDto getTrafficInfo() {
        return TrafficInfoDto.builder()
                .webSocketEndpoint("/api/v1/ws-traffic")
                .topic("/topic/traffic")
                .description("Connect to the WebSocket endpoint and subscribe to the topic to receive real-time HTTP traffic events")
                .build();
    }

    @GetMapping("/logs")
    @Operation(summary = "Get paginated HTTP traffic logs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully returned traffic logs")
    })
    public ResponseEntity<PageDto<TrafficLogEntryDto>> getTrafficLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String clientSessionId,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String pathContains,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        int resolvedSize = Math.max(1, Math.min(size, trafficProperties.getMaxPageSize()));
        return ResponseEntity.ok(PageDto.from(
                trafficLogService.findLogs(
                        clientSessionId,
                        method,
                        status,
                        pathContains,
                        text,
                        parseInstant(from),
                        parseInstant(to),
                        PageRequest.of(page, resolvedSize, DEFAULT_SORT)
                )
        ));
    }

    @GetMapping("/logs/{correlationId}")
    @Operation(summary = "Get traffic log by correlation identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully returned traffic log"),
            @ApiResponse(responseCode = "404", description = "Traffic log not found")
    })
    public ResponseEntity<TrafficLogEntryDto> getTrafficLog(@PathVariable String correlationId) {
        return trafficLogService.findByCorrelationId(correlationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid instant format: " + value, ex);
        }
    }
}
