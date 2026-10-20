package com.awesome.testing.traffic;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.user.Role;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
class TrafficPostgresIT extends DomainHelper {
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:16-alpine@sha256:"
                                            + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                            .asCompatibleSubstituteFor("postgres"));
    @Autowired private ObjectMapper mapper;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        POSTGRES.start();
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void capturedBodiesAreSearchableAndReadableImmediatelyOnPostgres() {
        var user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        var headers = getHeadersWith(getToken(user));
        headers.set(TrafficSession.HEADER, UUID.randomUUID().toString());
        assertThat(executeGet("/api/v1/users/me", headers, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        var response = executeGet("/api/v1/traffic/logs?text=" + user.getUsername(), headers, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = mapper.readTree(response.getBody());
        assertThat(body.path("content").size()).isEqualTo(1);
        assertThat(body.at("/content/0/responseBody/username").asString()).isEqualTo(user.getUsername());
        String correlation = body.at("/content/0/correlationId").asString();
        var detail = executeGet("/api/v1/traffic/logs/" + correlation, headers, String.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(detail.getBody()).at("/responseBody/username").asString())
                .isEqualTo(user.getUsername());
    }
}
