package com.awesome.testing.config;

import com.awesome.testing.HttpHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false"
})
class OpenApiContractTest extends HttpHelper {

    private JsonNode spec;

    @BeforeEach
    void loadGeneratedContract() throws Exception {
        spec = readApiSpec();
    }

    @Test
    void shouldKeepPublicEndpointsUnsecured() {
        // given
        var paths = List.of("/api/v1/users/signin", "/api/v1/users/signin/2fa", "/api/v1/users/signup",
                "/api/v1/users/refresh", "/api/v1/users/sso/exchange", "/api/v1/users/password/forgot", "/api/v1/users/password/reset");

        // when
        var operations = paths.stream().map(path -> getOperation(spec, path, "post")).toList();

        // then
        assertThat(operations).allSatisfy(operation -> assertThat(operation.path("security").isEmpty()).isTrue());
    }

    @Test
    void shouldDeclareBearerAuthenticationOnProtectedEndpoints() {
        // given
        var endpoints = List.of(
                new OpenApiDocument.Endpoint("/api/v1/products", "get"),
                new OpenApiDocument.Endpoint("/api/v1/orders", "get"),
                new OpenApiDocument.Endpoint("/api/v1/cart", "get"),
                new OpenApiDocument.Endpoint("/api/v1/users/me/email-events", "get"),
                new OpenApiDocument.Endpoint("/api/v1/users/2fa/status", "get"),
                new OpenApiDocument.Endpoint("/api/v1/users/2fa/setup", "post"),
                new OpenApiDocument.Endpoint("/api/v1/users/{username}/right-to-be-forgotten", "delete"));

        // when
        var operations = endpoints.stream().map(endpoint -> getOperation(spec, endpoint.path(), endpoint.method())).toList();

        // then
        assertThat(operations).allSatisfy(operation ->
                assertThat(operation.path("security").valueStream().anyMatch(security -> security.has("bearerAuth"))).isTrue());
        assertThat(spec.path("paths").has("/api/v1/local/email/outbox")).isFalse();
    }

    @Test
    void shouldDescribeEveryOperationAndSortItsResponses() {
        // given
        var document = new OpenApiDocument(spec);

        // when
        var operations = document.operations().toList();

        // then
        assertThat(spec.path("openapi").asText()).isNotBlank();
        assertThat(spec.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(operations).isNotEmpty().allSatisfy(operation -> {
            assertThat(operation.node().path("summary").asText()).as("%s summary", operation.endpoint()).isNotBlank();
            assertThat(operation.node().path("responses").isObject()).as("%s responses", operation.endpoint()).isTrue();
            assertThat(operation.responseCodes()).isNotEmpty();
            assertResponseCodesAreSorted(operation.node(), operation.endpoint().path(), operation.endpoint().method());
        });
    }

    @Test
    void shouldDocumentExpectedResponseCodes() {
        // given
        var document = spec;

        // when
        var paths = document.path("paths");

        // then
        assertThat(paths.isObject()).isTrue();
        assertDocumentedResponseCodes(document);
    }

    @Test
    void shouldHideRateLimitResponsesWhenDisabled() {
        // given
        var document = spec;

        // when
        var rateLimitResponses = new OpenApiDocument(document).operations()
                .flatMap(OpenApiDocument.DocumentedOperation::responses)
                .filter(response -> "429".equals(response.status()))
                .toList();

        // then
        assertThat(rateLimitResponses).isEmpty();
    }

    @Test
    void generatedOpenApiSpecShouldBeAvailableAsYaml() {
        // given
        var headers = getYamlHeaders();

        // when
        var response = executeGet("/v3/api-docs.yaml", headers, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank().contains("openapi:");
    }

    @Test
    void documentedSuccessValuesShouldAllowObservedNullsAndLocalTimestamps() {
        // given
        var components = spec.path("components");

        // when
        var schemas = components.path("schemas");

        // then
        List.of("token", "refreshToken", "challengeToken", "challengeExpiresAt").forEach(field -> {
            assertThat(schemas.path("LoginResponseDto").path("properties").path(field).path("type").toString())
                    .contains("\"string\"", "\"null\"");
        });
        List.of("Chat", "Tool").forEach(prefix -> {
            String field = prefix.toLowerCase(Locale.ROOT) + "SystemPrompt";
            assertThat(schemas.path(prefix + "SystemPromptDto").path("properties").path(field).path("type").toString())
                    .contains("\"string\"", "\"null\"");
        });
        JsonNode product = schemas.path("ProductDto").path("properties");
        assertThat(product.path("imageUrl").path("type").toString()).contains("\"string\"", "\"null\"");
        List.of("createdAt", "updatedAt").forEach(field -> {
            assertThat(product.path(field).path("format").asText()).isEqualTo("local-date-time");
        });
        assertThat(schemas.path("ProductCreateDto").path("properties").path("description").path("minLength").asInt())
                .isEqualTo(1);
    }

    @Test
    void documentedErrorsShouldUseErrorBodiesInsteadOfSuccessModels() {
        // given
        var document = new OpenApiDocument(spec);

        // when
        var validation = spec.path("components").path("schemas").path("ValidationErrorsDto");

        // then
        List.of(
                new OpenApiDocument.Endpoint("/api/v1/users/signin", "post"),
                new OpenApiDocument.Endpoint("/api/v1/users/refresh", "post"),
                new OpenApiDocument.Endpoint("/api/v1/users/chat-system-prompt", "put"),
                new OpenApiDocument.Endpoint("/api/v1/users/tool-system-prompt", "put"),
                new OpenApiDocument.Endpoint("/api/v1/products", "post"),
                new OpenApiDocument.Endpoint("/api/v1/products/{id}", "put"))
                .forEach(endpoint -> assertResponseSchemaRef(spec, endpoint.path(), endpoint.method(), "400", "ValidationErrorsDto"));
        List.of("/api/v1/users", "/api/v1/users/me", "/api/v1/users/{username}",
                "/api/v1/users/chat-system-prompt", "/api/v1/users/tool-system-prompt",
                "/api/v1/products", "/api/v1/products/{id}")
                .forEach(path -> assertResponseSchemaRef(spec, path, "get", "401", "ErrorDto"));
        assertResponseSchemaRef(spec, "/api/v1/users/signin", "post", "401", "ErrorDto");
        assertResponseSchemaRef(spec, "/api/v1/users/signin", "post", "422", "ErrorDto");
        assertResponseSchemaRef(spec, "/api/v1/users/refresh", "post", "401", "ErrorDto");
        assertResponseSchemaRef(spec, "/api/v1/products", "post", "403", "ErrorDto");
        assertResponseSchemaRef(spec, "/api/v1/products/{id}", "put", "403", "ErrorDto");
        assertThat(document.response(new OpenApiDocument.Endpoint("/api/v1/products/{id}", "delete"), "404").path("content").isMissingNode()).isTrue();
        assertThat(validation.path("additionalProperties").path("type").asText()).isEqualTo("string");
        assertThat(getOperation(spec, "/api/v1/users/signup", "post").path("responses").path("400")
                .path("content").path("application/json").path("schema").path("anyOf").toString())
                .contains("/ValidationErrorsDto", "/ErrorDto");
    }

    private JsonNode readApiSpec() throws Exception {
        ResponseEntity<String> response = executeGet("/v3/api-docs", getJsonOnlyHeaders(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
        return new ObjectMapper().readTree(response.getBody());
    }

    private HttpHeaders getYamlHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "*/*");
        return headers;
    }

    private void assertOperationResponsesContain(JsonNode spec, String path, String method, List<String> responseCodes) {
        JsonNode responses = getOperation(spec, path, method).path("responses");
        responseCodes.forEach(responseCode -> {
            assertThat(responses.has(responseCode))
                    .as("Expected response code %s for %s %s", responseCode, method.toUpperCase(Locale.ROOT), path)
                    .isTrue();
        });
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
        assertOperationResponsesContain(spec, "/api/v1/users/signin/2fa", "post", List.of("200", "400", "401"));
        assertOperationResponsesContain(spec, "/api/v1/users/2fa/status", "get", List.of("200", "401"));
        assertOperationResponsesContain(spec, "/api/v1/users/2fa/setup", "post", List.of("200", "401", "409"));
        assertOperationResponsesContain(spec, "/api/v1/users/2fa/confirm", "post", List.of("200", "400", "401", "409", "410"));
        assertOperationResponsesContain(spec, "/api/v1/users/2fa/recovery-codes", "post", List.of("200", "400", "401", "409"));
        assertOperationResponsesContain(spec, "/api/v1/users/2fa/disable", "post", List.of("200", "400", "401", "409"));
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
