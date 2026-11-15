package com.awesome.testing.event.broker;

import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.event.EventFeedService;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JMS message of one domain event (docs/EVENT_BROKER_CONTRACT.md in awesome-localstack): the body
 * is the event exactly as the feed serves it, the properties make it filterable, groupable and
 * deduplicable on the broker.
 */
public final class EventMessages {

    public static final String TYPE = "type";
    public static final String EVENT_ID = "eventId";
    public static final String SEQUENCE = "sequence";
    public static final String ORDER_ID = "orderId";
    public static final String TRACEPARENT = "traceparent";
    /** Message group: the broker hands one group to one consumer at a time, in order. */
    public static final String GROUP_ID = "JMSXGroupID";
    /** Artemis duplicate detection id. */
    public static final String DUPLICATE_ID = "_AMQ_DUPL_ID";

    /** A dedicated mapper: profile-specific Jackson settings (indentation) never reach the queue. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private EventMessages() {
    }

    /** The feed's JSON for one event: the same ten fields in the same order with the same bytes. */
    public static String body(DomainEventEntity event) {
        return JSON.writeValueAsString(EventFeedService.toDto(event));
    }

    /** {@code order-<id>} for an order's events, {@code event-<id>} otherwise. */
    public static String groupId(DomainEventEntity event) {
        return event.getOrderId() != null ? "order-" + event.getOrderId() : "event-" + event.getEventId();
    }

    public static TextMessage toMessage(Session session, DomainEventEntity event, String traceparent,
                                        boolean duplicateDetection) throws JMSException {
        TextMessage message = session.createTextMessage(body(event));
        message.setStringProperty(TYPE, event.getType());
        message.setStringProperty(EVENT_ID, event.getEventId().toString());
        message.setLongProperty(SEQUENCE, event.getSequence());
        if (event.getOrderId() != null) {
            message.setLongProperty(ORDER_ID, event.getOrderId());
        }
        message.setStringProperty(GROUP_ID, groupId(event));
        if (duplicateDetection) {
            message.setStringProperty(DUPLICATE_ID, event.getEventId().toString());
        }
        if (event.getRequestId() != null) {
            message.setJMSCorrelationID(event.getRequestId());
        }
        if (traceparent != null) {
            message.setStringProperty(TRACEPARENT, traceparent);
        }
        return message;
    }
}
