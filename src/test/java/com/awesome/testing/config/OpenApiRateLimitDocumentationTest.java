package com.awesome.testing.config;

import com.awesome.testing.HttpHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false",
        "security.rate-limit.enabled=true"
})
class OpenApiRateLimitDocumentationTest extends HttpHelper {

    private JsonNode spec;

    @BeforeEach
    void loadGeneratedContract() throws Exception {
        spec = readApiSpec();
    }

    @Test
    void generatedOpenApiSpecShouldDocumentRateLimitResponsesWhenRateLimitingIsEnabled() {
        // given
        var endpoints = rateLimitedOperations();

        // when
        var responses = endpoints.stream()
                .map(endpoint -> getOperation(spec, endpoint.path(), endpoint.method()).path("responses").path("429"))
                .toList();

        // then
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.isMissingNode()).isFalse();
            assertThat(response.path("content").path("application/json").path("schema").path("$ref").asText())
                    .endsWith("/ErrorDto");
        });
    }

    private JsonNode readApiSpec() throws Exception {
        ResponseEntity<String> response = executeGet("/v3/api-docs", getJsonOnlyHeaders(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
        return new ObjectMapper().readTree(response.getBody());
    }

    private List<RateLimitedOperation> rateLimitedOperations() {
        return List.of(
                new RateLimitedOperation("/api/v1/users/signup", "post"),
                new RateLimitedOperation("/api/v1/users/signin", "post"),
                new RateLimitedOperation("/api/v1/users/signin/2fa", "post"),
                new RateLimitedOperation("/api/v1/users/2fa/setup", "post"),
                new RateLimitedOperation("/api/v1/users/2fa/confirm", "post"),
                new RateLimitedOperation("/api/v1/users/2fa/recovery-codes", "post"),
                new RateLimitedOperation("/api/v1/users/2fa/disable", "post"),
                new RateLimitedOperation("/api/v1/users/refresh", "post"),
                new RateLimitedOperation("/api/v1/users/password/forgot", "post"),
                new RateLimitedOperation("/api/v1/users/password/reset", "post"),
                new RateLimitedOperation("/api/v1/email", "post"),
                new RateLimitedOperation("/api/v1/qr/create", "post"),
                new RateLimitedOperation("/api/v1/ollama/generate", "post"),
                new RateLimitedOperation("/api/v1/ollama/chat", "post"),
                new RateLimitedOperation("/api/v1/ollama/chat/tools", "post")
        );
    }

    private JsonNode getOperation(JsonNode spec, String path, String method) {
        JsonNode operation = spec.path("paths").path(path).path(method.toLowerCase(Locale.ROOT));
        assertThat(operation.isObject())
                .as("Missing operation %s %s", method.toUpperCase(Locale.ROOT), path)
                .isTrue();
        return operation;
    }

    private record RateLimitedOperation(String path, String method) {
    }
}
