package com.awesome.testing.endpoints.traffic;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.traffic.TrafficInfoDto;
import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.repository.TrafficLogRepository;
import com.awesome.testing.traffic.TrafficSession;
import java.util.List;
import java.util.Map;
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
    void anonymousCallersWithoutASessionHeaderReadEveryRecordAsBefore319() {
        // given: owner decision 2026-11-13 - the course API (awesome.byst.re) reads public traffic anonymously
        var user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        HttpHeaders owner = getHeadersWith(getToken(user));
        String marker = "marker-" + UUID.randomUUID();
        executeGet("/api/v1/users/me?marker=" + marker, owner, String.class);

        // when
        ResponseEntity<PageDto<TrafficLogEntryDto>> logsResponse = executeGet(
                API_TRAFFIC_LOGS + "?text=" + marker, getJsonOnlyHeaders(), new ParameterizedTypeReference<>() {});
        String correlationId = logsResponse.getBody().getContent().getFirst().getCorrelationId();
        ResponseEntity<TrafficLogEntryDto> detailResponse =
                executeGet(API_TRAFFIC_LOGS + "/" + correlationId, getJsonOnlyHeaders(), TrafficLogEntryDto.class);

        // then
        assertThat(logsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logsResponse.getBody().getContent()).singleElement()
                .satisfies(entry -> assertThat(entry.getPath()).isEqualTo("/api/v1/users/me"));
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody().getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    void anonymousCallersWithASessionHeaderReadThatSessionOnlyFollowingTheCourseBugReportSteps() {
        // given: l20/bug-reports/traffic-log-detail-returns-500.md - an anonymous caller with a unique session
        String session = "traffic-detail-bug-" + UUID.randomUUID();
        HttpHeaders anonymous = getJsonOnlyHeaders();
        anonymous.set(TrafficSession.HEADER, session);
        executeGet(API_TRAFFIC_INFO, anonymous, TrafficInfoDto.class);
        executeGet(API_TRAFFIC_INFO, getJsonOnlyHeaders(), TrafficInfoDto.class);

        // when: list the session's /traffic/info records, then open the listed one
        ResponseEntity<PageDto<TrafficLogEntryDto>> listed = executeGet(
                API_TRAFFIC_LOGS + "?pathContains=/api/v1/traffic/info&page=0&size=20", anonymous,
                new ParameterizedTypeReference<>() {});
        TrafficLogEntryDto record = listed.getBody().getContent().getFirst();
        ResponseEntity<TrafficLogEntryDto> detail =
                executeGet(API_TRAFFIC_LOGS + "/" + record.getCorrelationId(), anonymous, TrafficLogEntryDto.class);

        // then: only the session's record is listed. The detail answers 200: the 500 of the course report came
        // from large-object payload columns, which 3.9.0 (V6) replaced with text; no release since returned it.
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody().getContent()).singleElement()
                .satisfies(entry -> assertThat(entry.getClientSessionId()).isEqualTo(session));
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().getClientSessionId()).isEqualTo(session);
    }

    @Test
    void anonymousSessionReadsDoNotReachOtherSessionsAndStillMaskSecrets() {
        // given
        String session = "anonymous-" + UUID.randomUUID();
        HttpHeaders anonymous = getJsonOnlyHeaders();
        anonymous.set(TrafficSession.HEADER, session);
        var user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerUser(user);
        executePost("/api/v1/users/signin", Map.of("username", user.getUsername(), "password", user.getPassword()),
                anonymous, String.class);
        HttpHeaders otherSession = getHeadersWith(getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT))));
        executeGet("/api/v1/users/me", otherSession, String.class);
        String foreignId = executeGet(API_TRAFFIC_LOGS + "?pathContains=/api/v1/users/me", otherSession,
                new ParameterizedTypeReference<PageDto<TrafficLogEntryDto>>() {}).getBody().getContent()
                .getFirst().getCorrelationId();

        // when
        ResponseEntity<String> page = executeGet(API_TRAFFIC_LOGS, anonymous, String.class);
        ResponseEntity<String> foreign = executeGet(API_TRAFFIC_LOGS + "/" + foreignId, anonymous, String.class);

        // then
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("/api/v1/users/signin").doesNotContain(user.getPassword())
                .doesNotContain("/api/v1/users/me");
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
