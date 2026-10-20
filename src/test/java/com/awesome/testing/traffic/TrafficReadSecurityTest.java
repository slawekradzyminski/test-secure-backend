package com.awesome.testing.traffic;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.TrafficLogEntity;
import com.awesome.testing.repository.TrafficLogRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:trafficreadsecurity;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "app.traffic.persistence-flush-interval=3600000"
        })
class TrafficReadSecurityTest extends DomainHelper {
    @Autowired private TrafficLogService service;
    @Autowired private TrafficLogRepository repository;
    @Autowired private ObjectMapper mapper;

    @Test
    void sessionIdsAreCaseSensitiveForListsAndDetails() {
        var token = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        String session = "CaseSensitive-" + UUID.randomUUID();
        var own = row(session, "/own", Instant.now());
        var other = row(session.toLowerCase(Locale.ROOT), "/private", Instant.now());
        repository.saveAll(List.of(own, other));
        var headers = getHeadersWith(token);
        headers.set(TrafficSession.HEADER, session);

        var response = executeGet("/api/v1/traffic/logs", headers, String.class);
        var detail = executeGet("/api/v1/traffic/logs/" + other.getCorrelationId(), headers, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = mapper.readTree(response.getBody());
        assertThat(body.path("content").size()).isEqualTo(1);
        assertThat(body.at("/content/0/path").asString()).isEqualTo("/own");
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void logReadImmediatelyFlushesRequestsWithoutWaitingForTheBackgroundSchedule() {
        var token = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        var headers = getHeadersWith(token);
        headers.set(TrafficSession.HEADER, UUID.randomUUID().toString());
        var request = executeGet("/api/v1/users/me", headers, String.class);

        var response = executeGet("/api/v1/traffic/logs", headers, String.class);

        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = mapper.readTree(response.getBody());
        assertThat(body.path("content").size()).isEqualTo(1);
        assertThat(body.at("/content/0/path").asString()).isEqualTo("/api/v1/users/me");
        var correlation = body.at("/content/0/correlationId").asString();
        assertThat(executeGet("/api/v1/traffic/logs/" + correlation, headers, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void diagnosticActuatorEndpointsRequireAnAdministrator() {
        var client = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        var admin = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));

        assertThat(executeGet("/actuator/env", getHeadersWith(client), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(executeGet("/actuator/env", getHeadersWith(admin), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(executeGet("/actuator/health", getJsonOnlyHeaders(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void retentionBulkDeletePreservesTheBoundaryAndRecentRows() {
        Instant cutoff = Instant.parse("2026-10-20T00:00:00Z");
        var old = row("retention-session", "/expired", cutoff.minusSeconds(1));
        var boundary = row("retention-session", "/boundary", cutoff);
        var recent = row("retention-session", "/recent", cutoff.plusSeconds(1));
        repository.saveAll(List.of(old, boundary, recent));

        repository.deleteByTimestampBefore(cutoff);

        assertThat(repository.findByCorrelationId(old.getCorrelationId())).isEmpty();
        assertThat(repository.findByCorrelationId(boundary.getCorrelationId())).isPresent();
        assertThat(repository.findByCorrelationId(recent.getCorrelationId())).isPresent();
    }

    @Test
    void combinedFiltersKeepOnlyTheMatchingSessionMethodStatusBodyPathAndTime() {
        String session = "filterSession-" + UUID.randomUUID().toString();
        Instant at = Instant.parse("2026-10-20T00:00:00Z");
        var matching = filteredRow(session, "GET", 200, "/products/one", "needle", at);
        repository.saveAll(
                List.of(
                        matching,
                        filteredRow(session, "POST", 200, "/products/one", "needle", at),
                        filteredRow(session, "GET", 403, "/products/one", "needle", at),
                        filteredRow(session, "GET", 200, "/orders", "needle", at),
                        filteredRow(session, "GET", 200, "/products/one", "other", at),
                        filteredRow(session, "GET", 200, "/products/one", "needle", at.minusSeconds(1)),
                        filteredRow(session, "GET", 200, "/products/one", "needle", at.plusSeconds(1)),
                        filteredRow(session.toUpperCase(Locale.ROOT), "GET", 200, "/products/one", "needle", at)));

        var results =
                repository.findAll(
                        TrafficLogSpecifications.matching(session, "gEt", 200, "/PRODUCTS", "NEEDLE", at, at));

        assertThat(results).extracting(TrafficLogEntity::getCorrelationId).containsExactly(matching.getCorrelationId());
    }

    @Test
    void directDetailReadsFlushNewlyQueuedRecordsWithoutAListReadFirst() {
        var own = row("detail-session-1234", "/own-detail", Instant.now());
        service.save(own);
        assertThat(service.findByCorrelationIdAndClientSessionId(own.getCorrelationId(), own.getClientSessionId()))
                .hasValueSatisfying(log -> assertThat(log.getPath()).isEqualTo("/own-detail"));
        var legacy = row("legacy-session-1234", "/legacy-detail", Instant.now());
        service.save(legacy);
        assertThat(service.findByCorrelationId(legacy.getCorrelationId()))
                .hasValueSatisfying(log -> assertThat(log.getPath()).isEqualTo("/legacy-detail"));
    }

    private TrafficLogEntity filteredRow(
            String session, String method, int status, String path, String body, Instant time) {
        var result = row(session, path, time);
        result.setMethod(method);
        result.setStatus(status);
        result.setResponseBody(body);
        return result;
    }

    private TrafficLogEntity row(String session, String path, Instant time) {
        return TrafficLogEntity.builder()
                .correlationId(UUID.randomUUID().toString())
                .clientSessionId(session)
                .timestamp(time)
                .path(path)
                .method("GET")
                .status(200)
                .build();
    }
}
