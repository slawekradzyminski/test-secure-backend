package com.awesome.testing.config;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.config.properties.PasswordResetProperties;
import com.awesome.testing.traffic.TrafficProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "local"})
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false",
        "app.traffic.legacy-public-access=false",
        "password-reset.require-outbox-access-key=true",
        "password-reset.outbox-access-key=contract-audit-test-key"
})
class OpenApiSecurityProfilesTest extends HttpHelper {
    @Autowired
    private TrafficProperties trafficProperties;
    @Autowired
    private PasswordResetProperties passwordResetProperties;
    private JsonNode spec;

    @BeforeEach
    void readContract() throws Exception {
        var response = executeGet("/v3/api-docs", getJsonOnlyHeaders(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        spec = new ObjectMapper().readTree(response.getBody());
    }

    @Test
    void trafficSecurityAndSessionHeaderShouldMatchConfiguration() {
        // given
        var requiresAuthentication = !trafficProperties.isLegacyPublicAccess();
        var paths = List.of("/api/v1/traffic/info", "/api/v1/traffic/logs", "/api/v1/traffic/logs/{correlationId}");

        // when
        var operations = paths.stream().map(path -> spec.path("paths").path(path).path("get")).toList();

        // then
        assertThat(operations).allSatisfy(operation -> {
            assertThat(operation.path("security").valueStream().anyMatch(security -> security.has("bearerAuth")))
                    .isEqualTo(requiresAuthentication);
            assertThat(sessionHeader(operation).path("required").asBoolean()).isEqualTo(requiresAuthentication);
            assertThat(operation.path("responses").has("400")).isTrue();
            assertThat(operation.path("responses").has("401")).isTrue();
        });
    }

    @Test
    void localOutboxShouldRequireAdministratorBearerAndConfiguredAccessKey() {
        // given
        var requiresAccessKey = passwordResetProperties.isRequireOutboxAccessKey();
        var methods = List.of("get", "delete");

        // when
        var operations = methods.stream()
                .map(method -> spec.path("paths").path("/api/v1/local/email/outbox").path(method)).toList();

        // then
        assertThat(operations).allSatisfy(operation -> {
            var security = operation.path("security");
            assertThat(security.size()).isEqualTo(1);
            assertThat(security.get(0).has("bearerAuth")).isTrue();
            assertThat(security.get(0).has("localOutboxKey")).isEqualTo(requiresAccessKey);
            List.of("401", "403").forEach(status -> assertThat(operation.path("responses").path(status)
                    .path("content").path("application/json").path("schema").path("$ref").asText()).endsWith("/ErrorDto"));
        });
    }

    private JsonNode sessionHeader(JsonNode operation) {
        return operation.path("parameters").valueStream()
                .filter(parameter -> "X-Client-Session-Id".equals(parameter.path("name").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing traffic session header"));
    }
}
