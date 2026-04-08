package com.awesome.testing.endpoints.traffic;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.traffic.TrafficInfoDto;
import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.factory.UserFactory;
import com.awesome.testing.repository.TrafficLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class TrafficControllerTest extends DomainHelper {

    private static final String API_TRAFFIC_INFO = "/api/v1/traffic/info";
    private static final String API_TRAFFIC_LOGS = "/api/v1/traffic/logs";
    private static final String CLIENT_SESSION_ID = "session-" + UUID.randomUUID();

    @Autowired
    private TrafficLogRepository trafficLogRepository;

    @BeforeEach
    void setUp() {
        trafficLogRepository.deleteAll();
    }

    @Test
    void shouldReturnTrafficInfoWithoutAuthentication() {
        ResponseEntity<TrafficInfoDto> response = executeGet(
                API_TRAFFIC_INFO,
                getJsonOnlyHeaders(),
                TrafficInfoDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getWebSocketEndpoint()).isEqualTo("/api/v1/ws-traffic");
        assertThat(response.getBody().getTopic()).isEqualTo("/topic/traffic");
    }

    @Test
    void shouldReturnEmptyTrafficLogsPageWithoutAuthentication() {
        ResponseEntity<PageDto<TrafficLogEntryDto>> response = executeGet(
                API_TRAFFIC_LOGS,
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEmpty();
        assertThat(response.getBody().getTotalElements()).isZero();
    }

    @Test
    void shouldReturnPaginatedTrafficLogsAndExcludeTrafficEndpointRequests() {
        UserRegisterDto user = UserFactory.getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerUser(user);

        executePost(
                LOGIN_ENDPOINT,
                LoginDto.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .build(),
                getJsonOnlyHeaders(),
                LoginResponseDto.class
        );

        ResponseEntity<PageDto<TrafficLogEntryDto>> response = executeGet(
                API_TRAFFIC_LOGS + "?page=0&size=5&pathContains=/api/v1/users",
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isNotEmpty();
        assertThat(response.getBody().getContent())
                .allMatch(entry -> entry.getPath().startsWith("/api/v1/users"));
        assertThat(response.getBody().getContent())
                .noneMatch(entry -> entry.getPath().startsWith(API_TRAFFIC_LOGS));
        assertThat(response.getBody().getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldReturnTrafficLogByCorrelationId() {
        ResponseEntity<TrafficInfoDto> infoResponse = executeGet(
                API_TRAFFIC_INFO,
                getJsonOnlyHeaders(),
                TrafficInfoDto.class
        );

        assertThat(infoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<PageDto<TrafficLogEntryDto>> logsResponse = executeGet(
                API_TRAFFIC_LOGS + "?pathContains=/api/v1/traffic/info",
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(logsResponse.getBody()).isNotNull();
        TrafficLogEntryDto entry = logsResponse.getBody().getContent().getFirst();

        ResponseEntity<TrafficLogEntryDto> detailResponse = executeGet(
                API_TRAFFIC_LOGS + "/" + entry.getCorrelationId(),
                getJsonOnlyHeaders(),
                TrafficLogEntryDto.class
        );

        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody()).isNotNull();
        assertThat(detailResponse.getBody().getCorrelationId()).isEqualTo(entry.getCorrelationId());
        assertThat(detailResponse.getBody().getPath()).isEqualTo("/api/v1/traffic/info");
        assertThat(detailResponse.getBody().getRequestHeaders().isObject()).isTrue();
        assertThat(detailResponse.getBody().getResponseBody().isObject()).isTrue();
        assertThat(detailResponse.getBody().getResponseBody().get("webSocketEndpoint").asText())
                .isEqualTo("/api/v1/ws-traffic");
    }

    @Test
    void shouldReturnNotFoundForMissingCorrelationId() {
        ResponseEntity<TrafficLogEntryDto> response = executeGet(
                API_TRAFFIC_LOGS + "/" + UUID.randomUUID(),
                getJsonOnlyHeaders(),
                TrafficLogEntryDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldFilterByClientSessionIdAndSortNewestFirst() {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Client-Session-Id", CLIENT_SESSION_ID);

        ResponseEntity<TrafficInfoDto> firstResponse = executeGet(API_TRAFFIC_INFO, headers, TrafficInfoDto.class);
        ResponseEntity<TrafficInfoDto> secondResponse = executeGet(API_TRAFFIC_INFO, headers, TrafficInfoDto.class);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<PageDto<TrafficLogEntryDto>> logsResponse = executeGet(
                API_TRAFFIC_LOGS + "?clientSessionId=" + CLIENT_SESSION_ID,
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(logsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logsResponse.getBody()).isNotNull();
        assertThat(logsResponse.getBody().getContent()).hasSize(2);
        assertThat(logsResponse.getBody().getContent())
                .allMatch(entry -> CLIENT_SESSION_ID.equals(entry.getClientSessionId()));
        assertThat(logsResponse.getBody().getContent().get(0).getTimestamp())
                .isAfterOrEqualTo(logsResponse.getBody().getContent().get(1).getTimestamp());
    }

    @Test
    void shouldClampPageSizeAndAllowFilteringByStatusTextAndDateRange() {
        UserRegisterDto user = UserFactory.getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerUser(user);

        executePost(
                LOGIN_ENDPOINT,
                LoginDto.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .build(),
                getJsonOnlyHeaders(),
                LoginResponseDto.class
        );

        Instant from = Instant.now().minusSeconds(60);
        Instant to = Instant.now().plusSeconds(60);

        ResponseEntity<PageDto<TrafficLogEntryDto>> response = executeGet(
                API_TRAFFIC_LOGS
                        + "?size=500&status=200&text=" + user.getUsername()
                        + "&from=" + from
                        + "&to=" + to,
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPageSize()).isLessThanOrEqualTo(100);
        assertThat(response.getBody().getContent()).isNotEmpty();
        assertThat(response.getBody().getContent())
                .allMatch(entry -> entry.getStatus() == 200)
                .allMatch(entry -> !entry.getTimestamp().isBefore(from) && !entry.getTimestamp().isAfter(to))
                .anyMatch(entry -> entry.getRequestBody().toString().contains(user.getUsername())
                        || entry.getResponseBody().toString().contains(user.getUsername()));
    }

    @Test
    void shouldReturnBadRequestForInvalidFromInstant() {
        ResponseEntity<String> response = executeGet(
                API_TRAFFIC_LOGS + "?from=not-an-instant",
                getJsonOnlyHeaders(),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Invalid instant format");
    }

    @Test
    void shouldSkipActuatorAndSwaggerInfrastructureRequests() {
        ResponseEntity<String> actuatorResponse = executeGet("/actuator/health", getJsonOnlyHeaders(), String.class);
        ResponseEntity<String> swaggerResponse = executeGet("/v3/api-docs", getJsonOnlyHeaders(), String.class);
        ResponseEntity<String> swaggerConfigResponse = executeGet("/v3/api-docs/swagger-config", getJsonOnlyHeaders(), String.class);

        assertThat(actuatorResponse.getStatusCode().is2xxSuccessful() || actuatorResponse.getStatusCode() == HttpStatus.UNAUTHORIZED)
                .isTrue();
        assertThat(swaggerResponse.getStatusCode().is2xxSuccessful() || swaggerResponse.getStatusCode() == HttpStatus.UNAUTHORIZED)
                .isTrue();
        assertThat(swaggerConfigResponse.getStatusCode().is2xxSuccessful() || swaggerConfigResponse.getStatusCode() == HttpStatus.UNAUTHORIZED)
                .isTrue();

        ResponseEntity<PageDto<TrafficLogEntryDto>> actuatorLogs = executeGet(
                API_TRAFFIC_LOGS + "?pathContains=/actuator",
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );
        ResponseEntity<PageDto<TrafficLogEntryDto>> swaggerLogs = executeGet(
                API_TRAFFIC_LOGS + "?pathContains=/v3/api-docs",
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(actuatorLogs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(swaggerLogs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(actuatorLogs.getBody()).isNotNull();
        assertThat(swaggerLogs.getBody()).isNotNull();
        assertThat(actuatorLogs.getBody().getContent()).isEmpty();
        assertThat(swaggerLogs.getBody().getContent()).isEmpty();
    }

    @Test
    void shouldExposeStructuredHeadersAndBodyMetadata() {
        ResponseEntity<TrafficInfoDto> infoResponse = executeGet(
                API_TRAFFIC_INFO,
                getJsonOnlyHeaders(),
                TrafficInfoDto.class
        );

        assertThat(infoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<PageDto<TrafficLogEntryDto>> logsResponse = executeGet(
                API_TRAFFIC_LOGS + "?pathContains=/api/v1/traffic/info",
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(logsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logsResponse.getBody()).isNotNull();
        TrafficLogEntryDto entry = logsResponse.getBody().getContent().getFirst();

        assertThat(entry.getRequestHeaders().isObject()).isTrue();
        assertThat(entry.getResponseHeaders().isObject()).isTrue();
        assertThat(entry.getResponseBody().isObject()).isTrue();
        assertThat(entry.getResponseBody().get("topic").asText()).isEqualTo("/topic/traffic");
        assertThat(entry.isRequestBodyTruncated()).isFalse();
        assertThat(entry.isResponseBodyTruncated()).isFalse();
        assertThat(entry.getResponseBodyStoredLength()).isGreaterThan(0);
    }
}
