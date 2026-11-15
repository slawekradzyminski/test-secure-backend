package com.awesome.testing.event.broker;

import java.util.Locale;

/**
 * How commerce events reach the other services, selected by {@code events.transport}
 * ({@code EVENTS_TRANSPORT}). The feed endpoint is served with either value.
 */
public enum EventTransport {
    /** Consumers poll {@code GET /internal/v1/events}; nothing is sent to a broker. */
    FEED,
    /** The outbox relay also publishes every new event to the Artemis address {@code events.broker.address}. */
    BROKER;

    public static EventTransport from(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "feed" -> FEED;
            case "broker" -> BROKER;
            default -> throw new IllegalArgumentException(
                    "events.transport must be 'feed' or 'broker' but was '" + value + "'");
        };
    }
}
