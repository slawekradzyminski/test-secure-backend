package com.awesome.testing.endpoints.commerce;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.awesome.testing.dto.ollama.StreamedRequestDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.endpoints.ollama.OllamaMock;
import com.awesome.testing.event.DomainEventRecorder;
import com.awesome.testing.repository.event.DomainEventRepository;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static com.awesome.testing.factory.ollama.OllamaRequestFactory.validStreamedRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * {@code TRACING_ENABLED=true}: real spans, recorded by an in-memory exporter. The server span is
 * the child of the incoming W3C {@code traceparent}, the logged and stored trace and span ids are
 * the tracer's, and the instrumented Ollama {@code WebClient} propagates its client span.
 */
@EnableWireMock(@ConfigureWireMock(port = 0, portProperties = "wiremock.server.port",
        baseUrlProperties = "wiremock.server.baseUrl"))
@TestPropertySource(properties = {
        "app.tracing.enabled=true",
        // No OTLP collector in tests: the in-memory exporter below is the only one.
        "management.opentelemetry.tracing.export.otlp.endpoint=",
        "management.opentelemetry.tracing.export.schedule-delay=10ms"})
@Import(TracingEnabledTest.InMemoryExporter.class)
class TracingEnabledTest extends AbstractPaymentFlowTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String INVALID_PARENT = "0000000000000000";

    @TestConfiguration(proxyBeanMethods = false)
    static class InMemoryExporter {
        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }
    }

    @Autowired
    private InMemorySpanExporter spans;

    @Autowired
    private SdkTracerProvider tracerProvider;

    @Autowired
    private DomainEventRepository eventRepository;

    private final ListAppender<ILoggingEvent> recorderLog = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void captureRecorderLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(DomainEventRecorder.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        recorderLog.start();
        logger.addAppender(recorderLog);
    }

    @AfterEach
    void detachRecorderLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(DomainEventRecorder.class);
        logger.detachAppender(recorderLog);
        logger.setLevel(previousLevel);
    }

    @Test
    void theServerSpanContinuesTheIncomingTraceAndTheFeedAndLogsCarryTheTracersIds() {
        // given
        String traceId = "0af7651916cd43dd8448eb211c80319c";
        String parentId = "b7ad6b7169203331";
        long before = eventRepository.maxSequence();
        addToCart(clientToken, product("9.00", 5), 1);
        HttpHeaders headers = getHeadersWith(clientToken);
        headers.add("X-Request-Id", "traced-" + before);
        headers.add("traceparent", "00-" + traceId + "-" + parentId + "-01");

        // when
        ResponseEntity<OrderDto> checkout = executePost(ORDERS_ENDPOINT, getRandomAddress(), headers, OrderDto.class);

        // then
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(checkout.getHeaders().getFirst("X-Request-Id")).isEqualTo("traced-" + before);
        SpanData server = awaitServerSpan(traceId);
        assertThat(server.getParentSpanId()).isEqualTo(parentId);
        assertThat(server.getParentSpanContext().isRemote()).isTrue();
        JsonNode event = createdEvent(before, checkout.getBody().getId());
        assertThat(event.get("requestId").asString()).isEqualTo("traced-" + before);
        assertThat(event.get("traceId").asString()).isEqualTo(traceId);
        String spanId = event.get("spanId").asString();
        assertThat(spanIdsOf(traceId)).as("the stored span id is a real span of the trace").contains(spanId);
        assertThat(recorderLog.list).filteredOn(line -> line.getFormattedMessage().contains("order " + checkout.getBody().getId()))
                .singleElement().satisfies(line -> assertThat(line.getMDCPropertyMap())
                        .containsEntry("requestId", "traced-" + before)
                        .containsEntry("traceId", traceId)
                        .containsEntry("spanId", spanId));
    }

    @Test
    void withoutAnIncomingTraceparentTheServerSpanStartsTheTraceThatIsLoggedAndStored() {
        // given
        long before = eventRepository.maxSequence();
        addToCart(clientToken, product("7.00", 5), 1);

        // when
        ResponseEntity<OrderDto> checkout = executePost(ORDERS_ENDPOINT, getRandomAddress(),
                getHeadersWith(clientToken), OrderDto.class);

        // then
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode event = createdEvent(before, checkout.getBody().getId());
        String traceId = event.get("traceId").asString();
        SpanData server = awaitServerSpan(traceId);
        assertThat(server.getParentSpanId()).isEqualTo(INVALID_PARENT);
        assertThat(spanIdsOf(traceId)).contains(event.get("spanId").asString());
    }

    @Test
    void theOllamaClientPropagatesItsOwnSpanAsTheOnlyTraceparent() {
        // given
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        OllamaMock.stubSuccessfulGeneration();
        HttpHeaders headers = getHeadersWith(clientToken);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        headers.add("X-Request-Id", "ollama-traced");
        headers.add("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01");
        StreamedRequestDto request = validStreamedRequest();

        // when
        ResponseEntity<String> response = executePost("/api/v1/ollama/generate", request, headers, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoggedRequest call = findAll(postRequestedFor(urlEqualTo("/api/generate"))).getLast();
        assertThat(call.getHeader("X-Request-Id")).isEqualTo("ollama-traced");
        assertThat(call.getHeaders().getHeader("traceparent").values()).singleElement().satisfies(traceparent ->
                assertThat(traceparent).matches("00-" + traceId + "-[0-9a-f]{16}-01"));
        String clientSpanId = call.getHeader("traceparent").substring(36, 52);
        SpanData client = await().atMost(Duration.ofSeconds(10)).until(() -> flushed().stream()
                .filter(span -> span.getSpanId().equals(clientSpanId)).findFirst().orElse(null), span -> span != null);
        assertThat(client.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(client.getTraceId()).isEqualTo(traceId);
        assertThat(spanIdsOf(traceId)).as("the client span hangs under the request's spans")
                .contains(client.getParentSpanId());
    }

    @Test
    void scheduledJobTicksDoNotStartTraces() {
        // when: the traffic buffer flushes every 100 ms in the background
        await().pollDelay(Duration.ofMillis(500)).until(() -> true);

        // then
        assertThat(flushed()).noneMatch(span -> span.getKind() == SpanKind.INTERNAL
                && !span.getParentSpanContext().isValid());
    }

    private SpanData awaitServerSpan(String traceId) {
        return await().atMost(Duration.ofSeconds(10)).until(() -> flushed().stream()
                .filter(span -> span.getKind() == SpanKind.SERVER && span.getTraceId().equals(traceId))
                .filter(span -> span.getName().contains("/api/v1/orders"))
                .findFirst().orElse(null), span -> span != null);
    }

    private List<String> spanIdsOf(String traceId) {
        return flushed().stream().filter(span -> span.getTraceId().equals(traceId)).map(SpanData::getSpanId).toList();
    }

    private List<SpanData> flushed() {
        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        return spans.getFinishedSpanItems();
    }

    private JsonNode createdEvent(long after, Long orderId) {
        ResponseEntity<String> feed = executeGet("/internal/v1/events?after=" + after, serviceHeaders(WAREHOUSE_TOKEN),
                String.class);
        JsonNode events = JSON.readTree(feed.getBody()).get("events");
        return StreamSupport.stream(events.spliterator(), false)
                .filter(event -> event.get("orderId").asLong() == orderId)
                .filter(event -> event.get("type").asString().equals("order.created"))
                .findFirst().orElseThrow();
    }
}
