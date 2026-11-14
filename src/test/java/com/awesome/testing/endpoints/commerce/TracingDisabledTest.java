package com.awesome.testing.endpoints.commerce;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.endpoints.ollama.OllamaMock;
import com.awesome.testing.event.DomainEventRecorder;
import com.awesome.testing.repository.event.DomainEventRepository;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static com.awesome.testing.factory.ollama.OllamaRequestFactory.validStreamedRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for {@code TRACING_ENABLED=false} (the default) in the full context, with the tracer
 * on the classpath: nothing is recorded or exported, and {@code X-Request-Id}, the hand-written
 * child {@code traceparent} and the MDC ids are exactly those of the correlation code alone.
 */
@EnableWireMock(@ConfigureWireMock(port = 0, portProperties = "wiremock.server.port",
        baseUrlProperties = "wiremock.server.baseUrl"))
@TestPropertySource(properties = {
        "app.tracing.enabled=false",
        "management.opentelemetry.tracing.export.schedule-delay=10ms"})
@Import(TracingEnabledTest.InMemoryExporter.class)
class TracingDisabledTest extends AbstractPaymentFlowTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String PARENT_ID = "b7ad6b7169203331";

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
    void theRequestKeepsTheIncomingTraceIdAndItsOwnHandWrittenSpanInLogsAndFeed() {
        // given
        long before = eventRepository.maxSequence();
        addToCart(clientToken, product("9.00", 5), 1);
        HttpHeaders headers = getHeadersWith(clientToken);
        headers.add("X-Request-Id", "untraced-" + before);
        headers.add("traceparent", "00-" + TRACE_ID + "-" + PARENT_ID + "-01");

        // when
        ResponseEntity<OrderDto> checkout = executePost(ORDERS_ENDPOINT, getRandomAddress(), headers, OrderDto.class);

        // then
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(checkout.getHeaders().get("X-Request-Id")).containsExactly("untraced-" + before);
        assertThat(checkout.getHeaders().containsHeader("traceparent")).isFalse();
        JsonNode event = createdEvent(before, checkout.getBody().getId());
        assertThat(event.get("traceId").asString()).isEqualTo(TRACE_ID);
        String spanId = event.get("spanId").asString();
        assertThat(spanId).matches("[0-9a-f]{16}").isNotEqualTo(PARENT_ID);
        assertThat(recordedLine(checkout.getBody().getId()).getMDCPropertyMap())
                .containsEntry("requestId", "untraced-" + before)
                .containsEntry("traceId", TRACE_ID)
                .containsEntry("spanId", spanId);
        assertThat(exported()).isEmpty();
    }

    @Test
    void withoutHeadersTheGeneratedIdsAreTheOnesLoggedAndStored() {
        // given
        long before = eventRepository.maxSequence();
        addToCart(clientToken, product("8.00", 5), 1);

        // when
        ResponseEntity<OrderDto> checkout = executePost(ORDERS_ENDPOINT, getRandomAddress(),
                getHeadersWith(clientToken), OrderDto.class);

        // then
        JsonNode event = createdEvent(before, checkout.getBody().getId());
        assertThat(recordedLine(checkout.getBody().getId()).getMDCPropertyMap())
                .containsEntry("requestId", checkout.getHeaders().getFirst("X-Request-Id"))
                .containsEntry("traceId", event.get("traceId").asString())
                .containsEntry("spanId", event.get("spanId").asString());
        assertThat(exported()).isEmpty();
    }

    @Test
    void theOllamaClientSendsTheHandWrittenChildTraceparentOnly() {
        // given
        OllamaMock.stubSuccessfulGeneration();
        HttpHeaders headers = getHeadersWith(clientToken);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        headers.add("X-Request-Id", "ollama-untraced");
        headers.add("traceparent", "00-" + TRACE_ID + "-" + PARENT_ID + "-01");

        // when
        ResponseEntity<String> response = executePost("/api/v1/ollama/generate", validStreamedRequest(), headers,
                String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoggedRequest call = findAll(postRequestedFor(urlEqualTo("/api/generate"))).getLast();
        assertThat(call.getHeaders().getHeader("X-Request-Id").values()).containsExactly("ollama-untraced");
        assertThat(call.getHeaders().getHeader("traceparent").values()).singleElement().satisfies(traceparent ->
                assertThat(traceparent).matches("00-" + TRACE_ID + "-[0-9a-f]{16}-01")
                        .doesNotContain(PARENT_ID));
        assertThat(call.getHeaders().keys()).doesNotContain("tracestate", "baggage", "b3", "X-B3-TraceId");
        assertThat(exported()).isEmpty();
    }

    private ILoggingEvent recordedLine(Long orderId) {
        return recorderLog.list.stream()
                .filter(line -> line.getFormattedMessage().equals("Recorded order.created #"
                        + line.getArgumentArray()[1] + " for order " + orderId))
                .findFirst().orElseThrow();
    }

    private List<?> exported() {
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
