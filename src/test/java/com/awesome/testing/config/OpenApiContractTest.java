package com.awesome.testing.config;

import com.awesome.testing.HttpHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        assertPublicEndpointIsNotMarkedAsSecured(spec, "/api/v1/users/sso/exchange", "post");
        assertPublicEndpointIsNotMarkedAsSecured(spec, "/api/v1/users/password/forgot", "post");
        assertPublicEndpointIsNotMarkedAsSecured(spec, "/api/v1/users/password/reset", "post");

        assertSecuredEndpoint(spec, "/api/v1/products", "get");
        assertSecuredEndpoint(spec, "/api/v1/orders", "get");
        assertSecuredEndpoint(spec, "/api/v1/cart", "get");
        assertSecuredEndpoint(spec, "/api/v1/users/me/email-events", "get");
        assertSecuredEndpoint(spec, "/api/v1/users/{username}/right-to-be-forgotten", "delete");
        assertThat(spec.path("paths").has("/api/v1/local/email/outbox")).isFalse();

        assertDocumentedResponseCodes(spec);
        assertRateLimitResponsesAreHiddenWhenRateLimitingIsDisabled(spec);

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
                assertResponseCodesAreSorted(operation, path, method);
            }
        }
    }

    @Test
    void generatedOpenApiSpecShouldBeAvailableAsYaml() {
        assertYamlApiSpec("/v3/api-docs.yaml");
    }

    private JsonNode readApiSpec() throws Exception {
        ResponseEntity<String> response = executeGet("/v3/api-docs", getJsonOnlyHeaders(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
        return new ObjectMapper().readTree(response.getBody());
    }

    private void assertYamlApiSpec(String path) {
        ResponseEntity<String> response = executeGet(path, getYamlHeaders(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
        assertThat(response.getBody()).contains("openapi:");
    }

    private HttpHeaders getYamlHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "*/*");
        return headers;
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

    private void assertOperationResponsesDoNotContain(JsonNode spec, String path, String method, String responseCode) {
        JsonNode responses = getOperation(spec, path, method).path("responses");
        assertThat(responses.has(responseCode))
                .as("Did not expect response code %s for %s %s", responseCode, method.toUpperCase(Locale.ROOT), path)
                .isFalse();
    }

    private void assertDocumentedResponseCodes(JsonNode spec) {
        assertOperationResponsesContain(spec, "/api/v1/products", "get", List.of("200", "401"));
        assertOperationResponsesContain(spec, "/api/v1/products/{id}", "get", List.of("200", "400", "401", "404"));
        assertOperationResponsesContain(spec, "/api/v1/products", "post", List.of("201", "400", "401", "403"));
        assertOperationResponsesContain(spec, "/api/v1/products/{id}", "put", List.of("200", "400", "401", "403", "404"));
        assertOperationResponsesContain(spec, "/api/v1/products/{id}", "delete", List.of("204", "400", "401", "403", "404"));

        assertOperationResponsesContain(spec, "/api/v1/cart", "get", List.of("200", "401"));
        assertOperationResponsesContain(spec, "/api/v1/cart", "delete", List.of("204", "401"));
        assertOperationResponsesContain(spec, "/api/v1/cart/items", "post", List.of("200", "400", "401", "404"));
        assertOperationResponsesContain(spec, "/api/v1/cart/items/{productId}", "put", List.of("200", "400", "401", "404"));
        assertOperationResponsesContain(spec, "/api/v1/cart/items/{productId}", "delete", List.of("200", "400", "401", "404"));

        assertOperationResponsesContain(spec, "/api/v1/orders", "post", List.of("201", "400", "401"));
        assertOperationResponsesContain(spec, "/api/v1/orders", "get", List.of("200", "400", "401"));
        assertOperationResponsesContain(spec, "/api/v1/orders/{id}", "get", List.of("200", "400", "401", "404"));
        assertOperationResponsesContain(spec, "/api/v1/orders/{id}/status", "put", List.of("200", "400", "401", "403", "404"));
        assertOperationResponsesContain(spec, "/api/v1/orders/{id}/cancel", "post", List.of("200", "400", "401", "403", "404"));
        assertOperationResponsesContain(spec, "/api/v1/orders/admin", "get", List.of("200", "400", "401", "403"));

        assertOperationResponsesContain(spec, "/api/v1/users/signup", "post", List.of("201", "400"));
        assertOperationResponsesContain(spec, "/api/v1/users/signin", "post", List.of("200", "400", "422"));
        assertOperationResponsesContain(spec, "/api/v1/users/refresh", "post", List.of("200", "400", "401"));
        assertOperationResponsesContain(spec, "/api/v1/users/sso/exchange", "post", List.of("200", "400", "401", "404", "409"));
        assertResponseSchemaRef(spec, "/api/v1/users/sso/exchange", "post", "409", "ErrorDto");
        assertOperationResponsesContain(spec, "/api/v1/users/password/forgot", "post", List.of("202", "400"));
        assertOperationResponsesContain(spec, "/api/v1/users/password/reset", "post", List.of("200", "400"));
        assertOperationResponsesContain(spec, "/api/v1/users", "get", List.of("200", "401"));
        assertOperationResponsesContain(spec, "/api/v1/users/{username}", "get", List.of("200", "401", "404"));
        assertOperationResponsesContain(spec, "/api/v1/users/{username}", "put", List.of("200", "400", "401", "403", "404"));
        assertOperationResponsesContain(spec, "/api/v1/users/{username}", "delete", List.of("204", "401", "403", "404"));
        assertOperationResponsesContain(spec, "/api/v1/users/{username}/right-to-be-forgotten", "delete", List.of("204", "401", "403", "404"));
        assertOperationResponsesContain(spec, "/api/v1/users/me", "get", List.of("200", "401"));
        assertOperationResponsesContain(spec, "/api/v1/users/me/email-events", "get", List.of("200", "401"));
        assertOperationResponsesContain(spec, "/api/v1/users/logout", "post", List.of("200", "401"));
        assertOperationResponsesContain(spec, "/api/v1/users/chat-system-prompt", "get", List.of("200", "401"));
        assertOperationResponsesContain(spec, "/api/v1/users/chat-system-prompt", "put", List.of("200", "400", "401"));
        assertOperationResponsesContain(spec, "/api/v1/users/tool-system-prompt", "get", List.of("200", "401"));
        assertOperationResponsesContain(spec, "/api/v1/users/tool-system-prompt", "put", List.of("200", "400", "401"));

        assertOperationResponsesContain(spec, "/api/v1/email", "post", List.of("200", "400", "401"));
        assertOperationResponsesContain(spec, "/api/v1/qr/create", "post", List.of("200", "400", "401"));
        assertOperationResponsesContain(spec, "/api/v1/ollama/generate", "post", List.of("200", "400", "401", "404", "500"));
        assertResponseSchemaRef(spec, "/api/v1/ollama/generate", "post", "404", "ModelNotFoundDto");
        assertOperationResponsesContain(spec, "/api/v1/ollama/chat", "post", List.of("200", "400", "401", "404", "500"));
        assertResponseSchemaRef(spec, "/api/v1/ollama/chat", "post", "404", "ModelNotFoundDto");
        assertOperationResponsesContain(spec, "/api/v1/ollama/chat/tools", "post", List.of("200", "400", "401", "500"));
        assertOperationResponsesContain(spec, "/api/v1/ollama/chat/tools/definitions", "get", List.of("200", "401"));

        assertOperationResponsesContain(spec, "/api/v1/traffic/info", "get", List.of("200"));
        assertOperationResponsesContain(spec, "/api/v1/traffic/logs", "get", List.of("200", "400"));
        assertOperationResponsesContain(spec, "/api/v1/traffic/logs/{correlationId}", "get", List.of("200", "404"));
    }

    private void assertRateLimitResponsesAreHiddenWhenRateLimitingIsDisabled(JsonNode spec) {
        List.of(
                List.of("/api/v1/users/signup", "post"),
                List.of("/api/v1/users/signin", "post"),
                List.of("/api/v1/users/refresh", "post"),
                List.of("/api/v1/users/password/forgot", "post"),
                List.of("/api/v1/users/password/reset", "post"),
                List.of("/api/v1/email", "post"),
                List.of("/api/v1/qr/create", "post"),
                List.of("/api/v1/ollama/generate", "post"),
                List.of("/api/v1/ollama/chat", "post"),
                List.of("/api/v1/ollama/chat/tools", "post")
        ).forEach(operation -> assertOperationResponsesDoNotContain(spec, operation.get(0), operation.get(1), "429"));
    }

    private void assertResponseSchemaRef(JsonNode spec, String path, String method, String responseCode, String schemaName) {
        JsonNode schema = getOperation(spec, path, method)
                .path("responses")
                .path(responseCode)
                .path("content")
                .path("application/json")
                .path("schema");
        assertThat(schema.path("$ref").asText())
                .as("Expected schema %s for response %s on %s %s",
                        schemaName, responseCode, method.toUpperCase(Locale.ROOT), path)
                .endsWith("/" + schemaName);
    }

    private void assertResponseCodesAreSorted(JsonNode operation, String path, String method) {
        List<Integer> responseCodes = operation.path("responses")
                .properties()
                .stream()
                .map(Map.Entry::getKey)
                .filter(OpenApiContractTest::isNumericResponseCode)
                .map(Integer::parseInt)
                .toList();

        assertThat(responseCodes)
                .as("Response codes should be sorted for %s %s", method.toUpperCase(Locale.ROOT), path)
                .isSorted();
    }

    private static boolean isNumericResponseCode(String responseCode) {
        return responseCode.chars().allMatch(Character::isDigit);
    }

    private JsonNode getOperation(JsonNode spec, String path, String method) {
        JsonNode operation = spec.path("paths").path(path).path(method.toLowerCase(Locale.ROOT));
        assertThat(operation.isObject())
                .as("Missing operation %s %s in generated OpenAPI spec", method.toUpperCase(Locale.ROOT), path)
                .isTrue();
        return operation;
    }
}
