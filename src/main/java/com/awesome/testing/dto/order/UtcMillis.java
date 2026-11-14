package com.awesome.testing.dto.order;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** ISO-8601 UTC timestamps with exactly three fraction digits, for example {@code 2026-11-14T10:30:00.000Z}. */
public final class UtcMillis {

    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private UtcMillis() {
    }

    /** The formatted instant, truncated to milliseconds; null for null. */
    public static String format(Instant instant) {
        return instant == null ? null : UTC_MILLIS.format(instant.truncatedTo(ChronoUnit.MILLIS));
    }
}
