package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.event.DomainEventRetentionJob;
import com.awesome.testing.repository.event.DomainEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static org.assertj.core.api.Assertions.assertThat;

/** {@code GET /internal/v1/events}: the durable feed polled by warehouse-service and payment-service. */
class EventFeedEndpointTest extends AbstractPaymentFlowTest {

    private static final String EVENTS = "/internal/v1/events";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private DomainEventRepository eventRepository;

    @Autowired
    private CommerceEvents commerceEvents;

    @Autowired
    private DomainEventRetentionJob retentionJob;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsSettledEventsInSequenceOrderWithTheExactWireShapeForBothCallers() {
        // given
        long before = eventRepository.maxSequence();
        OrderDto order = order(clientToken, product("10.00", 5), 2);
        markPaid(order.getId());

        // when
        ResponseEntity<String> asPayment = feed("?after=" + before, PAYMENT_TOKEN);
        ResponseEntity<String> asWarehouse = feed("?after=" + before, WAREHOUSE_TOKEN);

        // then
        assertThat(asPayment.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asWarehouse.getBody()).isEqualTo(asPayment.getBody());
        JsonNode body = JSON.readTree(asPayment.getBody());
        assertThat(fieldNames(body)).containsExactly("events", "nextCursor", "hasMore");
        List<JsonNode> events = forOrder(body, order.getId());
        assertThat(events).extracting(event -> event.get("type").asString()).containsExactly("order.created", "order.paid");
        JsonNode created = events.getFirst();
        assertThat(fieldNames(created))
                .containsExactly("sequence", "id", "type", "occurredAt", "orderId", "username", "data", "requestId",
                        "traceId");
        assertThat(created.get("requestId").asString()).matches("[0-9a-f-]{36}");
        assertThat(created.get("traceId").asString()).matches("[0-9a-f]{32}");
        DomainEventEntity row = eventRepository.findByOrderIdOrderBySequenceAsc(order.getId()).getFirst();
        assertThat(created.get("sequence").asLong()).isEqualTo(row.getSequence()).isGreaterThan(before);
        assertThat(created.get("id").asString()).isEqualTo(row.getEventId().toString());
        assertThat(created.get("occurredAt").asString()).matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d(\\.\\d{3})?Z");
        assertThat(created.get("username").asString()).isEqualTo(client.getUsername());
        assertThat(asPayment.getBody()).contains("\"data\":{\"orderId\":" + order.getId()
                + ",\"status\":\"PENDING\",\"totalAmount\":20.00}");
        assertThat(body.get("hasMore").asBoolean()).isFalse();
        JsonNode all = body.get("events");
        assertThat(body.get("nextCursor").asLong()).isEqualTo(all.get(all.size() - 1).get("sequence").asLong());
    }

    @Test
    void feedEventsCarryTheRequestIdAndTraceIdOfTheRequestThatWroteThem() throws Exception {
        // given
        long before = eventRepository.maxSequence();
        addToCart(clientToken, product("9.00", 5), 1);
        HttpHeaders headers = getHeadersWith(clientToken);
        headers.add("X-Request-Id", "checkout-" + before);
        headers.add("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        // when
        ResponseEntity<OrderDto> checkout = executePost("/api/v1/orders", getRandomAddress(), headers, OrderDto.class);
        JsonNode body = JSON.readTree(feed("?after=" + before, WAREHOUSE_TOKEN).getBody());

        // then
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(checkout.getHeaders().getFirst("X-Request-Id")).isEqualTo("checkout-" + before);
        assertThat(forOrder(body, checkout.getBody().getId())).singleElement().satisfies(event -> {
            assertThat(event.get("type").asString()).isEqualTo("order.created");
            assertThat(event.get("requestId").asString()).isEqualTo("checkout-" + before);
            assertThat(event.get("traceId").asString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        });
    }

    @Test
    void eventsWrittenWithoutARequestHaveNoIds() throws Exception {
        // given
        long before = eventRepository.maxSequence();
        OrderDto order = order(clientToken, product("6.00", 5), 1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // when: published outside any request, like a scheduled job
        transaction.executeWithoutResult(status -> commerceEvents.publishData(CommerceEventType.ORDER_PAID, "job",
                order.getId(), Map.of("orderId", order.getId())));
        JsonNode body = JSON.readTree(feed("?after=" + before, WAREHOUSE_TOKEN).getBody());

        // then
        assertThat(forOrder(body, order.getId())).last().satisfies(event -> {
            assertThat(event.get("username").asString()).isEqualTo("job");
            assertThat(event.get("requestId").isNull()).isTrue();
            assertThat(event.get("traceId").isNull()).isTrue();
        });
    }

    @Test
    void pagesWithLimitAndCursorAndFiltersByType() {
        // given
        long before = eventRepository.maxSequence();
        OrderDto first = order(clientToken, product("3.00", 5), 1);
        OrderDto second = order(clientToken, product("4.00", 5), 1);
        markPaid(second.getId());

        // when
        JsonNode page1 = JSON.readTree(feed("?after=" + before + "&limit=1&types=order.created", PAYMENT_TOKEN).getBody());
        JsonNode page2 = JSON.readTree(feed("?after=" + page1.get("nextCursor").asLong()
                + "&limit=1&types=order.created", PAYMENT_TOKEN).getBody());
        JsonNode page3 = JSON.readTree(feed("?after=" + page2.get("nextCursor").asLong()
                + "&limit=1&types=order.created", PAYMENT_TOKEN).getBody());
        JsonNode mixed = JSON.readTree(feed("?after=" + before + "&types=order.paid,order.created", WAREHOUSE_TOKEN)
                .getBody());

        // then
        assertThat(page1.get("events")).hasSize(1);
        assertThat(page1.get("events").get(0).get("orderId").asLong()).isEqualTo(first.getId());
        assertThat(page1.get("hasMore").asBoolean()).isTrue();
        assertThat(page2.get("events").get(0).get("orderId").asLong()).isEqualTo(second.getId());
        assertThat(page2.get("hasMore").asBoolean()).isFalse();
        assertThat(page3.get("events")).isEmpty();
        assertThat(page3.get("nextCursor").asLong()).isEqualTo(page2.get("nextCursor").asLong());
        assertThat(page3.get("hasMore").asBoolean()).isFalse();
        List<String> types = new ArrayList<>();
        mixed.get("events").forEach(event -> types.add(event.get("type").asString()));
        assertThat(types).containsExactly("order.created", "order.created", "order.paid");
    }

    @Test
    void eventsRecordedWithinTheSettleDelayAreHeldBack() {
        // given
        long before = eventRepository.maxSequence();
        OrderDto order = order(clientToken, product("5.00", 5), 1);
        Long sequence = eventRepository.findByOrderIdOrderBySequenceAsc(order.getId()).getFirst().getSequence();
        jdbcTemplate.update("UPDATE domain_events SET recorded_at = ? WHERE sequence = ?",
                Timestamp.from(Instant.now().plus(Duration.ofMinutes(5))), sequence);

        // when
        JsonNode held = JSON.readTree(feed("?after=" + before, PAYMENT_TOKEN).getBody());
        jdbcTemplate.update("UPDATE domain_events SET recorded_at = ? WHERE sequence = ?",
                Timestamp.from(Instant.now().minus(Duration.ofSeconds(1))), sequence);
        JsonNode settled = JSON.readTree(feed("?after=" + before, PAYMENT_TOKEN).getBody());

        // then
        assertThat(forOrder(held, order.getId())).isEmpty();
        assertThat(forOrder(settled, order.getId())).singleElement()
                .satisfies(event -> assertThat(event.get("sequence").asLong()).isEqualTo(sequence));
    }

    @Test
    void badParametersAreRejected() {
        Map<String, String> cases = Map.of(
                "?after=-1", "after must be a non-negative integer",
                "?after=abc", "after must be a non-negative integer",
                "?limit=0", "limit must be between 1 and 200",
                "?limit=201", "limit must be between 1 and 200",
                "?limit=many", "limit must be between 1 and 200",
                "?types=order.created,ORDER_PAID", "Unknown event type 'ORDER_PAID'",
                "?types=webhook.ping", "Unknown event type 'webhook.ping'");

        cases.forEach((query, message) -> {
            ResponseEntity<ErrorDto> response = executeGet(EVENTS + query, serviceHeaders(PAYMENT_TOKEN), ErrorDto.class);
            assertThat(response.getStatusCode()).as(query).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getMessage()).as(query).isEqualTo(message);
        });
    }

    @Test
    void aWrongMissingOrJwtOnlyCallerIsUnauthorized() {
        HttpHeaders jwtOnly = getHeadersWith(adminToken);

        ResponseEntity<ErrorDto> wrong = executeGet(EVENTS, serviceHeaders("not-a-service-token"), ErrorDto.class);
        ResponseEntity<ErrorDto> missing = executeGet(EVENTS, getJsonOnlyHeaders(), ErrorDto.class);
        ResponseEntity<ErrorDto> jwt = executeGet(EVENTS, jwtOnly, ErrorDto.class);

        for (ResponseEntity<ErrorDto> response : List.of(wrong, missing, jwt)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().getMessage()).isEqualTo("Invalid service token");
        }
    }

    @Test
    void eventsAreRecordedInThePublishingTransactionOnly() {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        OrderDto order = order(clientToken, product("6.00", 5), 1);
        List<UUID> ids = new ArrayList<>();

        // when
        transaction.executeWithoutResult(status -> {
            ids.add(commerceEvents.publishData(CommerceEventType.ORDER_PAID, "rolled-back", order.getId(),
                    Map.of("orderId", order.getId())).id());
            status.setRollbackOnly();
        });
        transaction.executeWithoutResult(status -> ids.add(commerceEvents.publishData(CommerceEventType.ORDER_PAID,
                "committed", order.getId(), Map.of("orderId", order.getId())).id()));

        // then
        assertThat(eventRepository.findByEventId(ids.get(0))).isEmpty();
        assertThat(eventRepository.findByEventId(ids.get(1))).hasValueSatisfying(row -> {
            assertThat(row.getUsername()).isEqualTo("committed");
            assertThat(row.getPayload()).isEqualTo("{\"orderId\":" + order.getId() + "}");
        });
    }

    @Test
    void aFailedRequestLeavesNoEvent() {
        // given
        long before = eventRepository.maxSequence();
        OrderDto order = order(clientToken, product("6.00", 5), 1);

        // when: a refund of an order that was never delivered is rejected
        ResponseEntity<String> rejected = executePost("/internal/v1/orders/" + order.getId() + "/refunds",
                Map.of("requestId", UUID.randomUUID(), "amount", "1.00", "reference", "Return"),
                serviceHeaders(WAREHOUSE_TOKEN), String.class);

        // then
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(eventRepository.findByOrderIdOrderBySequenceAsc(order.getId())).extracting(DomainEventEntity::getType)
                .containsExactly("order.created");
        assertThat(eventRepository.maxSequence()).isGreaterThan(before);
    }

    @Test
    void retentionDeletesOnlyExpiredEvents() {
        // given
        OrderDto order = order(clientToken, product("7.00", 5), 1);
        markPaid(order.getId());
        List<DomainEventEntity> rows = eventRepository.findByOrderIdOrderBySequenceAsc(order.getId());
        jdbcTemplate.update("UPDATE domain_events SET recorded_at = ? WHERE sequence = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(31))), rows.getFirst().getSequence());

        // when
        int deleted = retentionJob.purge();

        // then
        assertThat(deleted).isPositive();
        assertThat(eventRepository.findByOrderIdOrderBySequenceAsc(order.getId())).extracting(DomainEventEntity::getType)
                .containsExactly("order.paid");
    }

    private ResponseEntity<String> feed(String query, String token) {
        return executeGet(EVENTS + query, serviceHeaders(token), String.class);
    }

    private static List<JsonNode> forOrder(JsonNode body, Long orderId) {
        List<JsonNode> events = new ArrayList<>();
        body.get("events").forEach(event -> {
            if (!event.get("orderId").isNull() && event.get("orderId").asLong() == orderId) {
                events.add(event);
            }
        });
        return events;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
