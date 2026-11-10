package com.awesome.testing.internal.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.awesome.testing.internal.security.ServiceCaller.PAYMENT;
import static com.awesome.testing.internal.security.ServiceCaller.WAREHOUSE;
import static org.assertj.core.api.Assertions.assertThat;

class InternalRoutesTest {

    private final InternalRoutes routes = InternalRoutes.builder()
            .route("/internal/v1/orders/\\d+/payment", PAYMENT)
            .route("/internal/v1/jwks\\.json", WAREHOUSE, PAYMENT)
            .route("/internal/v1/.*", WAREHOUSE)
            .otherwise(PAYMENT)
            .build();

    @Test
    void theFirstRuleMatchingTheWholePathWins() {
        assertThat(routes.callersFor("/internal/v1/orders/12/payment")).containsExactly(PAYMENT);
        assertThat(routes.callersFor("/internal/v1/jwks.json")).containsExactlyInAnyOrder(WAREHOUSE, PAYMENT);
        assertThat(routes.callersFor("/internal/v1/orders/12")).containsExactly(WAREHOUSE);
        assertThat(routes.callersFor("/internal/v1/orders/12/payment/x")).containsExactly(WAREHOUSE);
        assertThat(routes.callersFor("/internal/v1/orders/x/payment")).containsExactly(WAREHOUSE);
    }

    @Test
    void unmatchedPathsUseTheFallback() {
        assertThat(routes.callersFor("/internal")).containsExactly(PAYMENT);
        assertThat(routes.callersFor("/internal/v2/x")).containsExactly(PAYMENT);
    }

    @Test
    void withoutAFallbackUnmatchedPathsAcceptNobody() {
        InternalRoutes onlyPayment = InternalRoutes.builder().route("/internal/p", PAYMENT).build();

        assertThat(onlyPayment.callersFor("/internal/p")).isEqualTo(Set.of(PAYMENT));
        assertThat(onlyPayment.callersFor("/internal/q")).isEmpty();
    }
}
