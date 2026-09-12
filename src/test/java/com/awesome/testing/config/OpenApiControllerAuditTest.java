package com.awesome.testing.config;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.config.OpenApiDocument.DocumentedOperation;
import com.awesome.testing.config.OpenApiDocument.DocumentedResponse;
import com.awesome.testing.config.OpenApiDocument.Endpoint;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false"
})
class OpenApiControllerAuditTest extends HttpHelper {
    private static final Set<String> MESSAGE_ERROR_STATUSES = Set.of("401", "403", "409", "410", "422", "429");
    private static final Set<String> SUCCESS_SCHEMAS = Set.of(
            "LoginResponseDto", "CartDto", "OrderDto", "ProductDto", "UserEntity", "UserResponseDto",
            "InventoryItemDto", "TokenRefreshResponseDto", "ChatResponseDto", "GenerateResponseDto");
    private static final Set<Endpoint> EMPTY_NOT_FOUND_ENDPOINTS = Set.of(
            new Endpoint("/api/v1/products/{id}", "delete"),
            new Endpoint("/api/v1/traffic/logs/{correlationId}", "get"));

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private OpenApiDocument document;

    @BeforeEach
    void loadGeneratedContract() throws Exception {
        var response = executeGet("/v3/api-docs", getJsonOnlyHeaders(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        document = new OpenApiDocument(new ObjectMapper().readTree(response.getBody()));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi-audit-" + getClass().getSimpleName() + ".json"), document.root().toPrettyString());
    }

    @Test
    void shouldDocumentVoidSuccessResponsesWithoutBodies() {
        // given
        var endpoints = voidControllerEndpoints();

        // when
        var responses = endpoints.stream()
                .map(endpoint -> new DocumentedOperation(endpoint, document.operation(endpoint)))
                .flatMap(DocumentedOperation::responses)
                .filter(DocumentedResponse::isSuccess)
                .toList();

        // then
        assertThat(responses).isNotEmpty().allSatisfy(response ->
                assertThat(response.node().has("content")).as("%s response body", response).isFalse());
    }

    @Test
    void shouldDocumentQrSuccessAsPng() {
        // given
        var endpoint = new Endpoint("/api/v1/qr/create", "post");

        // when
        var content = document.response(endpoint, "200").path("content");

        // then
        assertThat(content.path("image/png").path("schema").path("format").asText()).isEqualTo("binary");
    }

    @Test
    void shouldDocumentOneDtoPerStreamingEvent() {
        // given
        var endpoints = List.of("generate", "chat", "chat/tools");

        // when
        var eventSchemas = endpoints.stream()
                .map(path -> new Endpoint("/api/v1/ollama/" + path, "post"))
                .map(endpoint -> document.response(endpoint, "200").path("content").path("text/event-stream").path("schema"))
                .toList();

        // then
        assertThat(eventSchemas).allSatisfy(schema -> assertThat(schema.path("$ref").asText()).endsWith("ResponseDto"));
    }

    @Test
    void shouldDocumentEmptyNotFoundResponses() {
        // given
        var endpoints = EMPTY_NOT_FOUND_ENDPOINTS;

        // when
        var responses = endpoints.stream().map(endpoint -> document.response(endpoint, "404")).toList();

        // then
        assertThat(responses).allSatisfy(response -> assertThat(response.has("content")).isFalse());
    }

    @Test
    void shouldDocumentErrorsWithJsonErrorSchemas() {
        // given
        var operations = document.operations();

        // when
        var errors = operations.flatMap(DocumentedOperation::responses)
                .filter(DocumentedResponse::isError)
                .filter(response -> !isEmptyNotFound(response))
                .toList();

        // then
        assertThat(errors).isNotEmpty().allSatisfy(this::assertJsonErrorContract);
    }

    @Test
    void shouldResolveEverySchemaReference() {
        // given
        var references = document.root().findValues("$ref");

        // when
        var paths = references.stream().map(JsonNode::asText).toList();

        // then
        assertThat(paths).isNotEmpty().allSatisfy(reference -> {
            assertThat(reference).startsWith("#/");
            assertThat(document.root().at(reference.substring(1)).isMissingNode()).as("Unresolved %s", reference).isFalse();
        });
    }

    @Test
    void shouldPreserveNonblankMinima() throws IOException {
        // given
        var fields = documentedFields().filter(field -> field.source().isAnnotationPresent(NotBlank.class)).toList();

        // when
        var constraints = fields.stream().map(this::resolveField).toList();

        // then
        assertThat(constraints).isNotEmpty().allSatisfy(field ->
                assertThat(field.schema().path("minLength").asInt()).as("%s nonblank minimum", field.name()).isPositive());
    }

    @Test
    void shouldDescribeLocalTimestampsWithoutAnOffset() throws IOException {
        // given
        var fields = documentedFields().filter(field -> field.source().getType() == LocalDateTime.class).toList();

        // when
        var timestamps = fields.stream().map(this::resolveField).toList();

        // then
        assertThat(timestamps).isNotEmpty().allSatisfy(field ->
                assertThat(field.schema().path("format").asText()).as("%s timestamp format", field.name()).isEqualTo("local-date-time"));
    }

    @Test
    void shouldDescribeTrafficBodiesAsJsonValues() {
        // given
        var fields = List.of("requestBody", "responseBody");

        // when
        var schemas = fields.stream().map(field -> document.property("TrafficLogEntryDto", field)).toList();

        // then
        assertThat(schemas).allSatisfy(schema -> {
            assertThat(schema.path("type").valueStream().map(JsonNode::asText).toList()).contains("string", "object", "array", "null");
            assertThat(schema.has("$ref")).isFalse();
        });
    }

    @Test
    void shouldDocumentProductPricePrecision() {
        // given
        var requests = List.of("ProductCreateDto", "ProductUpdateDto");

        // when
        var prices = requests.stream().map(request -> document.property(request, "price")).toList();

        // then
        assertThat(prices).allSatisfy(price -> {
            assertThat(price.path("multipleOf").decimalValue()).isEqualByComparingTo("0.01");
            assertThat(price.path("maximum").decimalValue()).isEqualByComparingTo("99999999.99");
        });
    }

    @Test
    void shouldMakeComputedValidationFieldsReadOnly() {
        // given
        var fields = List.of(new PropertyLocation("InventoryAdjustmentDto", "deltaNonZero"),
                new PropertyLocation("ChatMessageDto", "contentOrThinkingOrToolCallPresent"));

        // when
        var properties = fields.stream().map(this::resolveProperty).toList();

        // then
        assertThat(properties).allSatisfy(property -> assertThat(property.schema().path("readOnly").asBoolean()).as(property.name()).isTrue());
    }

    @Test
    void shouldExcludeZeroInventoryAdjustments() {
        // given
        var request = "InventoryAdjustmentDto";

        // when
        var delta = document.property(request, "delta");

        // then
        assertThat(delta.has("not")).isTrue();
    }

    @Test
    void shouldAllowNullInDocumentedOptionalBranches() {
        // given
        var fields = nullableFields();

        // when
        var properties = fields.map(this::resolveProperty).toList();

        // then
        assertThat(properties).allSatisfy(property -> assertThat(document.allowsNull(property.schema())).as(property.name()).isTrue());
    }

    @Test
    void shouldExcludePasswordsFromUserResponses() {
        // given
        var response = "UserEntity";

        // when
        var properties = document.root().path("components").path("schemas").path(response).path("properties");

        // then
        assertThat(properties.has("password")).isFalse();
    }

    @Test
    void shouldDocumentInventoryAndStockFailures() {
        // given
        var expectations = stockFailureExpectations();

        // when
        var operations = expectations.stream()
                .map(expected -> new ObservedStatuses(expected, document.operation(expected.endpoint()).path("responses")))
                .toList();

        // then
        assertThat(operations).allSatisfy(operation -> operation.expected().statuses().forEach(status ->
                assertThat(operation.responses().has(status)).as("%s -> %s", operation.expected().endpoint(), status).isTrue()));
    }

    @Test
    void shouldDescribeSsoValidationAsAFieldErrorMap() {
        // given
        var endpoint = new Endpoint("/api/v1/users/sso/exchange", "post");

        // when
        var schema = document.response(endpoint, "400").path("content").path("application/json").path("schema");

        // then
        assertThat(schema.path("$ref").asText()).endsWith("/ValidationErrorsDto");
    }

    private void assertJsonErrorContract(DocumentedResponse response) {
        var schema = response.jsonSchema();
        assertThat(response.node().path("content").size()).as("%s media types", response).isEqualTo(1);
        assertThat(schema.isMissingNode()).as("%s JSON error schema", response).isFalse();
        if (MESSAGE_ERROR_STATUSES.contains(response.status())) {
            assertThat(schema.path("$ref").asText()).as("%s message error", response).endsWith("/ErrorDto");
        }
        assertThat(schema.findValues("$ref")).extracting(JsonNode::asText)
                .as("%s must not use a success model", response)
                .noneMatch(reference -> SUCCESS_SCHEMAS.contains(reference.substring(reference.lastIndexOf('/') + 1)));
    }

    private boolean isEmptyNotFound(DocumentedResponse response) {
        return "404".equals(response.status()) && EMPTY_NOT_FOUND_ENDPOINTS.contains(response.endpoint());
    }

    private List<Endpoint> voidControllerEndpoints() {
        return requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("com.awesome.testing.controller"))
                .filter(entry -> returnsNoBody(entry.getValue()))
                .flatMap(entry -> entry.getKey().getPatternValues().stream()
                        .flatMap(path -> entry.getKey().getMethodsCondition().getMethods().stream()
                                .map(method -> new Endpoint(path, method.name()))))
                .toList();
    }

    private boolean returnsNoBody(HandlerMethod handler) {
        var type = ResolvableType.forMethodReturnType(handler.getMethod());
        return type.resolve() == void.class || (type.resolve() == ResponseEntity.class && type.getGeneric(0).resolve() == Void.class);
    }

    private Stream<DocumentedField> documentedFields() throws IOException {
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:com/awesome/testing/dto/**/*.class");
        return Arrays.stream(resources).map(this::readDtoClass)
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(field -> new DocumentedField(field, new PropertyLocation(field.getDeclaringClass().getSimpleName(), jsonName(field))))
                .filter(field -> !document.property(field.location().schema(), field.location().field()).isMissingNode());
    }

    private Class<?> readDtoClass(Resource resource) {
        try {
            var name = new SimpleMetadataReaderFactory().getMetadataReader(resource).getClassMetadata().getClassName();
            return ClassUtils.resolveClassName(name, getClass().getClassLoader());
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot inspect DTO " + resource, exception);
        }
    }

    private String jsonName(Field field) {
        var annotation = field.getAnnotation(JsonProperty.class);
        return annotation == null ? field.getName() : annotation.value();
    }

    private ResolvedProperty resolveField(DocumentedField field) {
        return resolveProperty(field.location());
    }

    private ResolvedProperty resolveProperty(PropertyLocation location) {
        return new ResolvedProperty(location.schema() + "." + location.field(), document.property(location.schema(), location.field()));
    }

    private Stream<PropertyLocation> nullableFields() {
        return Map.ofEntries(
                Map.entry("ForgotPasswordResponseDto", List.of("token")),
                Map.entry("EmailEventDto", List.of("failureReason")),
                Map.entry("InventoryMovementDto", List.of("orderId", "requestId")),
                Map.entry("UserEntity", List.of("authProvider", "providerSubject", "emailVerified", "chatSystemPrompt", "toolSystemPrompt")),
                Map.entry("UserResponseDto", List.of("firstName", "lastName")),
                Map.entry("TrafficLogEntryDto", List.of("queryString", "clientSessionId", "requestContentType", "responseContentType")),
                Map.entry("GenerateResponseDto", List.of("thinking", "context", "total_duration")),
                Map.entry("ChatMessageDto", List.of("content", "thinking", "tool_name")),
                Map.entry("OllamaToolParametersDto", List.of("oneOf", "required")),
                Map.entry("OllamaToolSchemaPropertyDto", List.of("enum")),
                Map.entry("ChatResponseDto", List.of("message")),
                Map.entry("EmailDto", List.of("template")))
                .entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(field -> new PropertyLocation(entry.getKey(), field)));
    }

    private List<ExpectedStatuses> stockFailureExpectations() {
        return List.of(
                new ExpectedStatuses(new Endpoint("/api/v1/admin/inventory/{productId}", "get"), List.of("400", "401", "403", "404")),
                new ExpectedStatuses(new Endpoint("/api/v1/admin/inventory/{productId}/movements", "get"), List.of("400", "401", "403", "404")),
                new ExpectedStatuses(new Endpoint("/api/v1/admin/inventory/{productId}/adjustments", "post"), List.of("400", "401", "403", "404", "409")),
                new ExpectedStatuses(new Endpoint("/api/v1/cart/items", "post"), List.of("409")),
                new ExpectedStatuses(new Endpoint("/api/v1/cart/items/{productId}", "put"), List.of("409")),
                new ExpectedStatuses(new Endpoint("/api/v1/orders", "post"), List.of("404", "409")));
    }

    private record PropertyLocation(String schema, String field) {}
    private record ResolvedProperty(String name, JsonNode schema) {}
    private record DocumentedField(Field source, PropertyLocation location) {}
    private record ExpectedStatuses(Endpoint endpoint, List<String> statuses) {}
    private record ObservedStatuses(ExpectedStatuses expected, JsonNode responses) {}
}
