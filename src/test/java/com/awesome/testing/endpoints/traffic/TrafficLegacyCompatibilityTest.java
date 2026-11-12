package com.awesome.testing.endpoints.traffic;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.traffic.TrafficInfoDto;
import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.repository.TrafficLogRepository;
import java.util.List;
import java.util.UUID;
import com.awesome.testing.dto.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
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
    void anonymousCallersGetAnEmptyPageAndNoDetailsEvenWhenTrafficExists() {
        // given: catalog X-03 - production (legacy public access) returned every user's traffic to anyone
        executeGet(API_TRAFFIC_INFO, getJsonOnlyHeaders(), TrafficInfoDto.class);
        var user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        HttpHeaders owner = getHeadersWith(getToken(user));
        executeGet("/api/v1/users/me", owner, String.class);
        String correlationId = executeGet(API_TRAFFIC_LOGS + "?pathContains=/api/v1/users/me", owner,
                new ParameterizedTypeReference<PageDto<TrafficLogEntryDto>>() {})
                .getBody().getContent().getFirst().getCorrelationId();

        // when
        ResponseEntity<PageDto<TrafficLogEntryDto>> logsResponse = executeGet(
                API_TRAFFIC_LOGS + "?page=0&size=3",
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );
        ResponseEntity<String> detailResponse =
                executeGet(API_TRAFFIC_LOGS + "/" + correlationId, getJsonOnlyHeaders(), String.class);

        // then: the page shape the course asserts, without content
        assertThat(logsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logsResponse.getBody()).isNotNull();
        assertThat(logsResponse.getBody().getContent()).isEmpty();
        assertThat(logsResponse.getBody().getTotalElements()).isZero();
        assertThat(logsResponse.getBody().getPageNumber()).isZero();
        assertThat(logsResponse.getBody().getPageSize()).isEqualTo(3);
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void authenticatedCallersWithoutASessionHeaderSeeOnlyTheirOwnRecords() {
        // given
        var alice = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        var bob = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        HttpHeaders aliceHeaders = getHeadersWith(getToken(alice));
        HttpHeaders bobHeaders = getHeadersWith(getToken(bob));
        String aliceMarker = "alice-" + UUID.randomUUID();
        executeGet("/api/v1/users/me?marker=" + aliceMarker, aliceHeaders, String.class);
        executeGet("/api/v1/users/me", bobHeaders, String.class);

        // when
        ResponseEntity<PageDto<TrafficLogEntryDto>> response = executeGet(API_TRAFFIC_LOGS + "?size=100",
                bobHeaders, new ParameterizedTypeReference<>() {});

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent())
                .extracting(TrafficLogEntryDto::getPath)
                .contains("/api/v1/users/me", "/api/v1/users/signin");
        assertThat(response.getBody().getContent())
                .extracting(TrafficLogEntryDto::getQueryString)
                .noneMatch(query -> query != null && query.contains(aliceMarker));
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
