package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.event.broker.EventMessages;
import com.awesome.testing.event.broker.EventRelay;
import com.awesome.testing.event.broker.EventTransport;
import com.awesome.testing.repository.event.DomainEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbox relay of the broker transport against the embedded Artemis broker: every new event is
 * published once, with exactly the feed's JSON as body and the documented properties
 * (docs/EVENT_BROKER_CONTRACT.md in awesome-localstack). The schedule is pushed out of the way; the
 * tests run the relay themselves.
 */
@TestPropertySource(properties = {
        "events.transport=broker",
        "events.broker.relay-initial-delay=PT1H",
        "events.broker.relay-interval=PT1H"})
class EventBrokerRelayTest extends AbstractPaymentFlowTest {

    private static final String ADDRESS = "awesome.commerce.events";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private EventRelay relay;

    @Autowired
    private DomainEventRepository eventRepository;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private MeterRegistry meterRegistry;

    private Connection connection;
    private MessageConsumer subscriber;

    @BeforeEach
    void subscribe() throws JMSException {
        connection = connectionFactory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        subscriber = session.createConsumer(session.createTopic(ADDRESS));
        connection.start();
        relay.relay();
        drain();
    }

    @AfterEach
    void close() throws JMSException {
        connection.close();
    }

    @Test
    void publishesEveryNewEventOnceWithTheFeedBodyAndTheBrokerProperties() throws JMSException {
        // given
        assertThat(relay.getTransport()).isEqualTo(EventTransport.BROKER);
        long before = eventRepository.maxSequence();
        OrderDto order = order(clientToken, product("10.00", 5), 2);
        markPaid(order.getId());

        // when
        int sent = relay.relay();

        // then
        assertThat(sent).isGreaterThanOrEqualTo(2);
        List<TextMessage> messages = forOrder(drain(), order.getId());
        assertThat(messages).extracting(message -> message.getStringProperty(EventMessages.TYPE))
                .containsExactly("order.created", "order.paid");
        String feedBody = executeGet("/internal/v1/events?after=" + before, serviceHeaders(PAYMENT_TOKEN), String.class)
                .getBody();
        JsonNode feed = JSON.readTree(feedBody);
        for (TextMessage message : messages) {
            long sequence = message.getLongProperty(EventMessages.SEQUENCE);
            JsonNode feedEvent = feedEvent(feed, sequence);
            assertThat(JSON.readTree(message.getText())).as("body of #%d is the feed's event", sequence)
                    .isEqualTo(feedEvent);
            assertThat(feedBody).as("the feed serves the same bytes").contains(message.getText());
            DomainEventEntity row = eventRepository.findByEventId(UUID.fromString(feedEvent.get("id").asString()))
                    .orElseThrow();
            assertThat(message.getStringProperty(EventMessages.EVENT_ID)).isEqualTo(row.getEventId().toString());
            assertThat(message.getLongProperty(EventMessages.ORDER_ID)).isEqualTo(order.getId());
            assertThat(message.getStringProperty(EventMessages.GROUP_ID)).isEqualTo("order-" + order.getId());
            assertThat(message.getJMSCorrelationID()).isEqualTo(row.getRequestId());
            assertThat(message.getStringProperty(EventMessages.TRACEPARENT))
                    .startsWith("00-" + row.getTraceId() + "-").matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");
            assertThat(row.getPublishedAt()).isNotNull();
        }
        assertThat(relay.relay()).as("nothing is sent twice").isZero();
        assertThat(drain()).isEmpty();
        assertThat(relay.getPending()).isZero();
    }

    @Test
    void eventsOlderThanTheMaximumAgeAreMarkedPublishedWithoutBeingSent() throws JMSException {
        // given
        DomainEventEntity old = eventRepository.save(DomainEventEntity.builder()
                .eventId(UUID.randomUUID())
                .type("order.paid")
                .orderId(987_654L)
                .occurredAt(Instant.now().minus(Duration.ofDays(2)))
                .recordedAt(Instant.now().minus(Duration.ofDays(2)))
                .payload("{\"orderId\":987654}")
                .build());

        // when
        relay.relay();

        // then
        assertThat(forOrder(drain(), 987_654L)).isEmpty();
        assertThat(eventRepository.findByEventId(old.getEventId()).orElseThrow().getPublishedAt()).isNotNull();
    }

    @Test
    void anEventTheBrokerAlreadyHoldsIsMarkedPublishedAndTheRestOfTheBatchStillFlows() throws JMSException {
        // given an event the broker received before the relay could set published_at (a crash, a reset database)
        DomainEventEntity delivered = eventRepository.save(event(987_655L));
        DomainEventEntity next = eventRepository.save(event(987_656L));
        try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageProducer producer = session.createProducer(session.createTopic(ADDRESS))) {
            producer.send(EventMessages.toMessage(session, delivered, null, true));
        }
        assertThat(forOrder(drain(), 987_655L)).hasSize(1);
        double duplicatesBefore = meterRegistry.get("events.relay.duplicates").counter().count();

        // when
        int sent = relay.relay();

        // then the batch is not wedged: the duplicate is skipped, the next event is delivered once
        List<TextMessage> messages = drain();
        assertThat(forOrder(messages, 987_655L)).as("no second copy of the delivered event").isEmpty();
        assertThat(forOrder(messages, 987_656L)).hasSize(1);
        assertThat(sent).isEqualTo(1);
        assertThat(eventRepository.findByEventId(delivered.getEventId()).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(eventRepository.findByEventId(next.getEventId()).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(meterRegistry.get("events.relay.duplicates").counter().count()).isEqualTo(duplicatesBefore + 1);
        assertThat(relay.relay()).isZero();
        assertThat(relay.getPending()).isZero();
    }

    private static DomainEventEntity event(long orderId) {
        return DomainEventEntity.builder()
                .eventId(UUID.randomUUID())
                .type("order.paid")
                .orderId(orderId)
                .occurredAt(Instant.now())
                .recordedAt(Instant.now())
                .payload("{\"orderId\":" + orderId + "}")
                .build();
    }

    private List<TextMessage> drain() throws JMSException {
        List<TextMessage> messages = new ArrayList<>();
        Message message;
        while ((message = subscriber.receive(500)) != null) {
            messages.add((TextMessage) message);
        }
        return messages;
    }

    private static List<TextMessage> forOrder(List<TextMessage> messages, long orderId) throws JMSException {
        List<TextMessage> result = new ArrayList<>();
        for (TextMessage message : messages) {
            if (message.propertyExists(EventMessages.ORDER_ID) && message.getLongProperty(EventMessages.ORDER_ID) == orderId) {
                result.add(message);
            }
        }
        return result;
    }

    private static JsonNode feedEvent(JsonNode feed, long sequence) {
        for (JsonNode event : feed.get("events")) {
            if (event.get("sequence").asLong() == sequence) {
                return event;
            }
        }
        throw new AssertionError("Event #" + sequence + " is not on the feed");
    }
}
