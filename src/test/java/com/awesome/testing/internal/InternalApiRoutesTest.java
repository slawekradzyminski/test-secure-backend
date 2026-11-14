package com.awesome.testing.internal;

import com.awesome.testing.internal.security.InternalRoutes;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.awesome.testing.internal.security.ServiceCaller.NOTIFICATION;
import static com.awesome.testing.internal.security.ServiceCaller.PAYMENT;
import static com.awesome.testing.internal.security.ServiceCaller.WAREHOUSE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The production route table: payment-service gets its result endpoint, order reads, the JWKS and the
 * feed; notification-service gets the JWKS, the feed and the webhook export only.
 */
class InternalApiRoutesTest {

    private final InternalRoutes routes = new InternalApiConfig().internalRoutes();

    @Test
    void paymentResultsAcceptOnlyThePaymentCaller() {
        assertThat(routes.callersFor("/internal/v1/orders/42/payment")).isEqualTo(Set.of(PAYMENT));
    }

    @Test
    void orderReadsAcceptWarehouseAndPaymentAndTheJwksAcceptsEveryCaller() {
        assertThat(routes.callersFor("/internal/v1/orders/42")).isEqualTo(Set.of(WAREHOUSE, PAYMENT));
        assertThat(routes.callersFor("/internal/v1/jwks.json")).isEqualTo(Set.of(WAREHOUSE, PAYMENT, NOTIFICATION));
    }

    @Test
    void theEventFeedAcceptsEveryCaller() {
        assertThat(routes.callersFor("/internal/v1/events")).isEqualTo(Set.of(WAREHOUSE, PAYMENT, NOTIFICATION));
        assertThat(routes.callersFor("/internal/v1/events/1")).isEqualTo(Set.of(WAREHOUSE));
    }

    @Test
    void theWebhookExportAcceptsOnlyTheNotificationCaller() {
        assertThat(routes.callersFor("/internal/v1/webhooks/export")).isEqualTo(Set.of(NOTIFICATION));
        assertThat(routes.callersFor("/internal/v1/webhooks/export/1")).isEqualTo(Set.of(WAREHOUSE));
        assertThat(routes.callersFor("/internal/v1/webhooks")).isEqualTo(Set.of(WAREHOUSE));
    }

    @Test
    void everythingElseStaysWarehouseOnly() {
        for (String path : new String[] {"/internal/v1/orders", "/internal/v1/orders/42/refunds",
                "/internal/v1/orders/42/fulfilment", "/internal/v1/orders/42/warehouse-cancel",
                "/internal/v1/orders/42/payment/extra", "/internal/v1/orders/abc", "/internal/v1/jwksXjson",
                "/internal/v1/products", "/internal/v1/notifications", "/internal/v1/inventory/receipts"}) {
            assertThat(routes.callersFor(path)).as(path).isEqualTo(Set.of(WAREHOUSE));
        }
    }
}
