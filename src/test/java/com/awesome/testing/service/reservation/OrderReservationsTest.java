package com.awesome.testing.service.reservation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderReservationsTest {

    private static final Instant CREATED = Instant.parse("2026-11-14T10:00:00.123987Z");

    @Test
    void theReservationEndsTtlAfterCreationAtMillisecondPrecision() {
        assertThat(new OrderReservations(true, Duration.ofMinutes(30)).expiryFor(CREATED))
                .isEqualTo(Instant.parse("2026-11-14T10:30:00.123Z"));
        assertThat(new OrderReservations(true, Duration.ofSeconds(1)).expiryFor(CREATED))
                .isEqualTo(Instant.parse("2026-11-14T10:00:01.123Z"));
    }

    @Test
    void disabledReservationsGiveNewOrdersNoEnd() {
        assertThat(new OrderReservations(false, Duration.ofMinutes(30)).expiryFor(CREATED)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1S"})
    void rejectsATtlThatIsNotPositive(String ttl) {
        assertThatThrownBy(() -> new OrderReservations(true, Duration.parse(ttl)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orders.reservation.ttl must be positive");
    }

    @Test
    void rejectsAMissingTtl() {
        assertThatThrownBy(() -> new OrderReservations(true, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsTheSmallestPositiveTtl() {
        assertThat(new OrderReservations(true, Duration.ofNanos(1)).expiryFor(CREATED))
                .isEqualTo(Instant.parse("2026-11-14T10:00:00.123Z"));
    }
}
