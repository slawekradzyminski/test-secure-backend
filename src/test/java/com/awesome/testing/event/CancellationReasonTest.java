package com.awesome.testing.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationReasonTest {

    @Test
    void reservationExpiryIsPerformedByTheSystem() {
        assertThat(CancellationReason.RESERVATION_EXPIRED.actorFor("buyer")).isEqualTo("system");
    }

    @ParameterizedTest
    @EnumSource(value = CancellationReason.class, names = "RESERVATION_EXPIRED", mode = EnumSource.Mode.EXCLUDE)
    void otherCancellationsKeepTheCustomerAsActor(CancellationReason reason) {
        assertThat(reason.actorFor("buyer")).isEqualTo("buyer");
    }
}
