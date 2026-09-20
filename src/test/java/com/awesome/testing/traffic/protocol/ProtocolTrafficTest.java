package com.awesome.testing.traffic.protocol;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.TrafficLogEntity;
import com.awesome.testing.repository.TrafficLogRepository;
import com.awesome.testing.traffic.TrafficPublisher;
import com.awesome.testing.traffic.TrafficSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:protocoltraffic;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.traffic.legacy-public-access=false",
        "app.traffic.obfuscate-authorization=false",
        "app.traffic.obfuscate-sensitive-body-fields=false"
})
class ProtocolTrafficTest extends DomainHelper {
    @Autowired private TrafficLogRepository logs;
    @Autowired private ObjectMapper mapper;
    @Autowired private Queue<TrafficEventDto> events;
    @MockitoBean private TrafficPublisher publisher;

    @BeforeEach
    void clearTraffic() {
        logs.deleteAll();
        events.clear();
    }

    @Test
    void capturesPartialErrorsWithoutDocumentsAliasesVariablesHeadersOrErrorMessages() {
        // given
        var token = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        var session = UUID.randomUUID().toString();
        var headers = headers(token, session);
        headers.set("X-Secret", "PRIVATE_HEADER");
        var document = "query PRIVATE_OPERATION { PRIVATE_ALIAS: cart(username:\"PRIVATE_OWNER\"){username} products(limit:1){items{id}} }";

        // when
        var response = executePost("/api/v1/graphql?secret=PRIVATE_URL", Map.of("query", document,
                "variables", Map.of("shippingAddress", "PRIVATE_ADDRESS")), headers, String.class);
        var entry = recorded(response.getHeaders().getFirst("X-Correlation-Id"));
        var summary = mapper.readTree(entry.getResponseBody());
        var ownLog = executeGet("/api/v1/traffic/logs/" + entry.getCorrelationId(), headers, String.class);
        var otherLog = executeGet("/api/v1/traffic/logs/" + entry.getCorrelationId(),
                headers(token, UUID.randomUUID().toString()), String.class);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(mapper.readTree(response.getBody()).has("errors")).isTrue();
        assertThat(entry.getStatus()).isEqualTo(200);
        assertThat(entry.getClientSessionId()).isEqualTo(session);
        assertThat(entry.getQueryString()).isNull();
        assertThat(entry.getRequestHeaders()).isEqualTo("{}");
        assertThat(entry.getRequestBody()).isEqualTo("{}");
        assertThat(entry.getResponseHeaders()).isEqualTo("{}");
        assertThat(entry.getResponseBody()).doesNotContain("PRIVATE", token, "Access denied", "shippingAddress");
        assertThat(summary.path("protocol").asText()).isEqualTo("GRAPHQL");
        assertThat(summary.path("operation").asText()).isEqualTo("query cart,products");
        assertThat(summary.path("outcome").asText()).isEqualTo("PARTIAL_ERROR");
        assertThat(summary.path("codes").toString()).contains("FORBIDDEN");
        assertThat(summary.path("correlationId").asText()).isEqualTo(entry.getCorrelationId());
        assertThat(entry.getDurationMs()).isNotNegative();
        assertThat(ownLog.getStatusCode().value()).isEqualTo(200);
        assertThat(otherLog.getStatusCode().value()).isEqualTo(404);
        assertThat(events.stream().filter(event -> event.getProtocolDetails() != null).toList()).singleElement()
                .satisfies(event -> assertThat(event.getProtocolDetails().outcome()).isEqualTo("PARTIAL_ERROR"));
    }

    @Test
    void successfulOperationsProduceOneRecordAndRestEventShapeStaysUnchanged() {
        // given
        var token = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));

        // when
        var response = executePost("/api/v1/graphql", Map.of("query", "{cart {totalItems}}"),
                headers(token, UUID.randomUUID().toString()), String.class);
        var entry = recorded(response.getHeaders().getFirst("X-Correlation-Id"));

        // then
        assertThat(mapper.readTree(entry.getResponseBody()).path("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(logs.findAll().stream().filter(row -> "GRAPHQL".equals(row.getMethod()))).hasSize(1);
        assertThat(mapper.writeValueAsString(TrafficEventDto.builder().method("GET").status(200).build()))
                .doesNotContain("protocolDetails");
    }

    @Test
    void recordsRejectedTransportRequestsWithoutInspectingTheirBodies() {
        // given
        var session = UUID.randomUUID().toString();

        // when
        var response = executePost("/api/v1/graphql", Map.of("query", "PRIVATE_DOCUMENT"), headers(null, session), String.class);
        var entry = recorded(response.getHeaders().getFirst("X-Correlation-Id"));

        // then
        assertThat(entry.getStatus()).isEqualTo(401);
        assertThat(entry.getResponseBody()).contains("TRANSPORT_ERROR", "ERROR").doesNotContain("PRIVATE_DOCUMENT");
    }

    @Test
    void recordsValidationErrorsAndHonorsTheActualGraphqlResponseHttpStatus() {
        // given
        var token = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        var headers = headers(token, UUID.randomUUID().toString());
        headers.set("Accept", "application/graphql-response+json");

        // when
        var response = executePost("/api/v1/graphql", Map.of("query", "{ PRIVATE_UNKNOWN_FIELD }"), headers, String.class);
        var entry = recorded(response.getHeaders().getFirst("X-Correlation-Id"));

        // then
        assertThat(entry.getStatus()).isEqualTo(response.getStatusCode().value());
        assertThat(mapper.readTree(entry.getResponseBody()).path("outcome").asText()).isEqualTo("ERROR");
        assertThat(entry.getResponseBody()).doesNotContain("PRIVATE_UNKNOWN_FIELD");
    }

    @Test
    void captureRequiresAValidExplicitSession() {
        // given
        var token = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));

        // when
        var absent = executePost("/api/v1/graphql", Map.of("query", "{cart{totalItems}}"), getHeadersWith(token), String.class);
        var invalid = executePost("/api/v1/graphql", Map.of("query", "{cart{totalItems}}"), headers(token, "short"), String.class);

        // then
        assertThat(absent.getStatusCode().value()).isEqualTo(200);
        assertThat(invalid.getStatusCode().value()).isEqualTo(200);
        assertThat(absent.getHeaders().getFirst("X-Correlation-Id")).isNull();
        assertThat(invalid.getHeaders().getFirst("X-Correlation-Id")).isNull();
        assertThat(logs.findAll().stream().filter(row -> "GRAPHQL".equals(row.getMethod()))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("operationDocuments")
    void summarizesOnlyTheSelectedOperationAndNeverClientNames(String document, String name, String expected) {
        // given
        var token = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        var input = new HashMap<String, Object>();
        input.put("query", document);
        if (name != null) {
            input.put("operationName", name);
        }

        // when
        var response = executePost("/api/v1/graphql", input, headers(token, UUID.randomUUID().toString()), String.class);
        var entry = recorded(response.getHeaders().getFirst("X-Correlation-Id"));

        // then
        assertThat(mapper.readTree(entry.getResponseBody()).path("operation").asText()).isEqualTo(expected);
        assertThat(entry.getResponseBody()).doesNotContain("PRIVATE");
    }

    static Stream<Arguments> operationDocuments() {
        return Stream.of(
                Arguments.of("query PRIVATE_A {products {total}} query PRIVATE_B {cart {totalItems}}", "PRIVATE_B", "query cart"),
                Arguments.of("query PRIVATE_A {cart {totalItems}}", "PRIVATE_MISSING", "unresolved"),
                Arguments.of("query PRIVATE_A {products {total}} query PRIVATE_B {cart {totalItems}}", null, "unresolved"),
                Arguments.of("query PRIVATE_B { ...PRIVATE_FIELDS } fragment PRIVATE_FIELDS on Query {cart {totalItems}}", null, "query"),
                Arguments.of("{ PRIVATE_ALIAS: cart {totalItems}", null, "invalid"));
    }

    private HttpHeaders headers(String token, String session) {
        var headers = token == null ? getJsonOnlyHeaders() : getHeadersWith(token);
        headers.set(TrafficSession.HEADER, session);
        return headers;
    }

    private TrafficLogEntity recorded(String correlation) {
        assertThat(correlation).isNotBlank();
        await().atMost(Duration.ofSeconds(5)).until(() -> logs.findByCorrelationId(correlation).isPresent());
        return logs.findByCorrelationId(correlation).orElseThrow();
    }
}
