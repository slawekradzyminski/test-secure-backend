package com.awesome.testing.config;

import com.awesome.testing.HttpHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "local"})
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false"
})
class OpenApiContractLocalProfileTest extends HttpHelper {

    @Test
    void generatedOpenApiSpecShouldContainLocalOutboxEndpoints() throws Exception {
        ResponseEntity<String> response = executeGet("/v3/api-docs", getJsonOnlyHeaders(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();

        JsonNode spec = new ObjectMapper().readTree(response.getBody());
        JsonNode outboxPath = spec.path("paths").path("/local/email/outbox");
        assertThat(outboxPath.isObject()).isTrue();

        JsonNode getOperation = outboxPath.path("get");
        assertThat(getOperation.path("summary").asText()).isNotBlank();
        assertThat(getOperation.path("responses").has("200")).isTrue();

        JsonNode deleteOperation = outboxPath.path("delete");
        assertThat(deleteOperation.path("summary").asText()).isNotBlank();
        assertThat(deleteOperation.path("responses").has("200")).isTrue();
    }
}
