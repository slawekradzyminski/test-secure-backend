package com.awesome.testing.dto.order;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UtcMillisTest {

    @Test
    void alwaysWritesThreeFractionDigitsInUtc() {
        assertThat(UtcMillis.format(Instant.parse("2026-11-14T10:30:00Z"))).isEqualTo("2026-11-14T10:30:00.000Z");
        assertThat(UtcMillis.format(Instant.parse("2026-11-14T10:30:00.5Z"))).isEqualTo("2026-11-14T10:30:00.500Z");
    }

    @Test
    void truncatesBelowMilliseconds() {
        assertThat(UtcMillis.format(Instant.parse("2026-11-14T23:59:59.999999Z"))).isEqualTo("2026-11-14T23:59:59.999Z");
    }

    @Test
    void nullStaysNull() {
        assertThat(UtcMillis.format(null)).isNull();
    }
}
