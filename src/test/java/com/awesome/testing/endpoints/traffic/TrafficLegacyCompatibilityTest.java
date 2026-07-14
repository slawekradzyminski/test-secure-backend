package com.awesome.testing.endpoints.traffic;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.traffic.TrafficInfoDto;
import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.repository.TrafficLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@TestPropertySource(properties = "app.traffic.legacy-public-access=true")
class TrafficLegacyCompatibilityTest extends DomainHelper {

    private static final String API_TRAFFIC_INFO = "/api/v1/traffic/info";
    private static final String API_TRAFFIC_LOGS = "/api/v1/traffic/logs";

    @Autowired
    private TrafficLogRepository trafficLogRepository;

    @BeforeEach
    void setUp() {
        trafficLogRepository.deleteAll();
    }

    @Test
    void shouldExposeGlobalTrafficInfoWithoutAuthenticationOrSessionHeader() {
        ResponseEntity<TrafficInfoDto> response = executeGet(
                API_TRAFFIC_INFO,
                getJsonOnlyHeaders(),
                TrafficInfoDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getWebSocketEndpoint()).isEqualTo("/api/v1/ws-traffic");
        assertThat(response.getBody().getTopic()).isEqualTo("/topic/traffic");
    }

    @Test
    void shouldExposeGlobalTrafficLogsAndDetailsWithoutAuthenticationOrSessionHeader() {
        executeGet(API_TRAFFIC_INFO, getJsonOnlyHeaders(), TrafficInfoDto.class);

        ResponseEntity<PageDto<TrafficLogEntryDto>> logsResponse = executeGet(
                API_TRAFFIC_LOGS + "?pathContains=/api/v1/traffic/info",
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(logsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logsResponse.getBody()).isNotNull();
        assertThat(logsResponse.getBody().getContent()).isNotEmpty();
        TrafficLogEntryDto logEntry = logsResponse.getBody().getContent().getFirst();

        ResponseEntity<TrafficLogEntryDto> detailResponse = executeGet(
                API_TRAFFIC_LOGS + "/" + logEntry.getCorrelationId(),
                getJsonOnlyHeaders(),
                TrafficLogEntryDto.class
        );

        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody()).isNotNull();
        assertThat(detailResponse.getBody().getCorrelationId()).isEqualTo(logEntry.getCorrelationId());
    }

    @Test
    void shouldReturnNotFoundForUnknownGlobalTrafficLog() {
        ResponseEntity<TrafficLogEntryDto> response = executeGet(
                API_TRAFFIC_LOGS + "/" + UUID.randomUUID(),
                getJsonOnlyHeaders(),
                TrafficLogEntryDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
