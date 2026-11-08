package com.awesome.testing.jms;

import java.util.Locale;

/** Where outgoing emails go; selected by {@code app.email.transport}. */
public enum EmailTransport {
    JMS,
    LOCAL_OUTBOX;

    public static EmailTransport from(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "jms" -> JMS;
            case "local-outbox" -> LOCAL_OUTBOX;
            default -> throw new IllegalArgumentException(
                    "app.email.transport must be 'jms' or 'local-outbox' but was '" + value + "'");
        };
    }
}
