package com.awesome.testing.internal.security;

import com.awesome.testing.internal.security.ServiceTokenGuard.Decision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.awesome.testing.internal.security.ServiceCaller.PAYMENT;
import static com.awesome.testing.internal.security.ServiceCaller.WAREHOUSE;
import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenGuardTest {

    private final ServiceTokenGuard guard = new ServiceTokenGuard(Map.of(
            WAREHOUSE, "s3cret-token",
            PAYMENT, "payment-token"));

    @Test
    void acceptsOnlyTheExactTokenOfAnAcceptedCaller() {
        Set<ServiceCaller> warehouse = Set.of(WAREHOUSE);

        assertThat(guard.isEnabled(WAREHOUSE)).isTrue();
        assertThat(guard.check(warehouse, "s3cret-token")).isEqualTo(Decision.ACCEPTED);
        assertThat(guard.check(warehouse, "s3cret-token ")).isEqualTo(Decision.REJECTED);
        assertThat(guard.check(warehouse, "s3cret-toke")).isEqualTo(Decision.REJECTED);
        assertThat(guard.check(warehouse, "S3CRET-TOKEN")).isEqualTo(Decision.REJECTED);
        assertThat(guard.check(warehouse, "")).isEqualTo(Decision.REJECTED);
        assertThat(guard.check(warehouse, null)).isEqualTo(Decision.REJECTED);
    }

    @Test
    void aTokenOfAnotherCallerIsRejected() {
        assertThat(guard.check(Set.of(WAREHOUSE), "payment-token")).isEqualTo(Decision.REJECTED);
        assertThat(guard.check(Set.of(PAYMENT), "s3cret-token")).isEqualTo(Decision.REJECTED);
        assertThat(guard.check(Set.of(PAYMENT), "payment-token")).isEqualTo(Decision.ACCEPTED);
    }

    @Test
    void anEndpointSharedByCallersAcceptsEitherToken() {
        Set<ServiceCaller> both = EnumSet.allOf(ServiceCaller.class);

        assertThat(guard.check(both, "s3cret-token")).isEqualTo(Decision.ACCEPTED);
        assertThat(guard.check(both, "payment-token")).isEqualTo(Decision.ACCEPTED);
        assertThat(guard.check(both, "other")).isEqualTo(Decision.REJECTED);
    }

    @Test
    void anEndpointAcceptingNoCallerDoesNotExist() {
        assertThat(guard.check(List.of(), "s3cret-token")).isEqualTo(Decision.DISABLED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void aBlankConfiguredTokenDisablesOnlyThatCaller(String configured) {
        Map<ServiceCaller, String> tokens = new HashMap<>();
        tokens.put(WAREHOUSE, "s3cret-token");
        tokens.put(PAYMENT, configured);
        ServiceTokenGuard paymentDisabled = new ServiceTokenGuard(tokens);

        assertThat(paymentDisabled.isEnabled(PAYMENT)).isFalse();
        assertThat(paymentDisabled.isEnabled(WAREHOUSE)).isTrue();
        assertThat(paymentDisabled.check(Set.of(PAYMENT), configured)).isEqualTo(Decision.DISABLED);
        assertThat(paymentDisabled.check(Set.of(PAYMENT), "s3cret-token")).isEqualTo(Decision.DISABLED);
        assertThat(paymentDisabled.check(Set.of(WAREHOUSE), "s3cret-token")).isEqualTo(Decision.ACCEPTED);
        assertThat(paymentDisabled.check(EnumSet.allOf(ServiceCaller.class), "s3cret-token"))
                .as("a shared endpoint stays reachable for the enabled caller")
                .isEqualTo(Decision.ACCEPTED);
        assertThat(paymentDisabled.check(EnumSet.allOf(ServiceCaller.class), "x")).isEqualTo(Decision.REJECTED);
    }
}
