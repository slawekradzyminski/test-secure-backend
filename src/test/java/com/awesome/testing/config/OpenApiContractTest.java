package com.awesome.testing.config;

import com.awesome.testing.HttpHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false"
})
class OpenApiContractTest extends HttpHelper {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "delete", "patch", "head", "options", "trace"
    );

    @Test
    void generatedOpenApiSpecShouldContainAccurateSecurityAndOperationMetadata() throws Exception {
        JsonNode spec = readApiSpec();

        assertThat(spec.path("openapi").asText()).isNotBlank();
        assertThat(spec.path("components").path("securitySchemes").has("bearerAuth")).isTrue();

        assertPublicEndpointIsNotMarkedAsSecured(spec, "/api/v1/users/signin", "post");
        assertPublicEndpointIsNotMarkedAsSecured(spec, "/api/v1/users/signup", "post");
        assertPublicEndpointIsNotMarkedAsSecured(spec, "/api/v1/users/refresh", "post");
        assertPublicEndpointIsNotMarkedAsSecured(spec, "/api/v1/users/password/forgot", "post");
        assertPublicEndpointIsNotMarkedAsSecured(spec, "/api/v1/users/password/reset", "post");

        assertSecuredEndpoint(spec, "/api/v1/products", "get");
        assertSecuredEndpoint(spec, "/api/v1/orders", "get");
        assertSecuredEndpoint(spec, "/api/v1/cart", "get");
        assertSecuredEndpoint(spec, "/api/v1/users/me/email-events", "get");
        assertSecuredEndpoint(spec, "/api/v1/users/{username}/right-to-be-forgotten", "delete");
        assertThat(spec.path("paths").has("/api/v1/local/email/outbox")).isFalse();

        assertOperationResponsesContain(spec, "/api/v1/users/signin", "post", List.of("200", "400", "422"));
        assertOperationResponsesContain(spec, "/api/v1/products", "get", List.of("200", "401"));

        JsonNode paths = spec.path("paths");
        assertThat(paths.isObject()).isTrue();
        Iterator<String> pathIterator = paths.fieldNames();
        while (pathIterator.hasNext()) {
            String path = pathIterator.next();
            JsonNode pathItem = paths.path(path);
            Iterator<String> operationIterator = pathItem.fieldNames();
            while (operationIterator.hasNext()) {
                String method = operationIterator.next().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    continue;
                }
                JsonNode operation = pathItem.path(method);
                assertThat(operation.path("summary").asText())
                        .as("Missing summary for %s %s", method.toUpperCase(Locale.ROOT), path)
                        .isNotBlank();
                assertThat(operation.path("responses").isObject() && operation.path("responses").size() > 0)
                        .as("Missing responses for %s %s", method.toUpperCase(Locale.ROOT), path)
                        .isTrue();
            }
        }
    }

    private JsonNode readApiSpec() throws Exception {
        ResponseEntity<String> response = executeGet("/v3/api-docs", getJsonOnlyHeaders(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
        return new ObjectMapper().readTree(response.getBody());
    }

    private void assertPublicEndpointIsNotMarkedAsSecured(JsonNode spec, String path, String method) {
        JsonNode operation = getOperation(spec, path, method);
        JsonNode security = operation.path("security");
        assertThat(security.isMissingNode() || security.isEmpty())
                .as("Public endpoint %s %s should not require auth in docs", method.toUpperCase(Locale.ROOT), path)
                .isTrue();
    }

    private void assertSecuredEndpoint(JsonNode spec, String path, String method) {
        JsonNode operation = getOperation(spec, path, method);
        JsonNode security = operation.path("security");
        assertThat(security.isArray() && security.size() > 0)
                .as("Secured endpoint %s %s should declare security", method.toUpperCase(Locale.ROOT), path)
                .isTrue();
        boolean containsBearerAuth = false;
        for (JsonNode securityRequirement : security) {
            if (securityRequirement.has("bearerAuth")) {
                containsBearerAuth = true;
                break;
            }
        }
        assertThat(containsBearerAuth)
                .as("Secured endpoint %s %s should use bearerAuth", method.toUpperCase(Locale.ROOT), path)
                .isTrue();
    }

    private void assertOperationResponsesContain(JsonNode spec, String path, String method, List<String> responseCodes) {
        JsonNode responses = getOperation(spec, path, method).path("responses");
        for (String responseCode : responseCodes) {
            assertThat(responses.has(responseCode))
                    .as("Expected response code %s for %s %s", responseCode, method.toUpperCase(Locale.ROOT), path)
                    .isTrue();
        }
    }

    private JsonNode getOperation(JsonNode spec, String path, String method) {
        JsonNode operation = spec.path("paths").path(path).path(method.toLowerCase(Locale.ROOT));
        assertThat(operation.isObject())
                .as("Missing operation %s %s in generated OpenAPI spec", method.toUpperCase(Locale.ROOT), path)
                .isTrue();
        return operation;
    }
}
