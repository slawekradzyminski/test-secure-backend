package com.awesome.testing.service.payment;

import com.awesome.testing.dto.payment.PaymentRefundRequestDto;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceClientTest {

    private static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final PaymentRefundRequestDto REFUND =
            new PaymentRefundRequestDto(REQUEST, 3L, new BigDecimal("7.50"), "Return #3");

    @BeforeAll
    static void start() {
        SERVER.start();
    }

    @AfterAll
    static void stop() {
        SERVER.stop();
    }

    @BeforeEach
    void reset() {
        SERVER.resetAll();
    }

    @Test
    void postsTheRefundWithTheServiceTokenAndReadsTheRefundId() {
        UUID refundId = UUID.randomUUID();
        SERVER.stubFor(post(urlEqualTo("/internal/v1/payments/refunds")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"refundId\":\"" + refundId + "\",\"paymentId\":\"" + UUID.randomUUID()
                        + "\",\"amount\":7.50,\"status\":\"PARTIALLY_REFUNDED\",\"extra\":true}")));

        RefundDelivery delivery = client("secret-token").refund(REFUND);

        assertThat(delivery).isEqualTo(RefundDelivery.refunded(refundId));
        SERVER.verify(postRequestedFor(urlEqualTo("/internal/v1/payments/refunds"))
                .withHeader("X-Service-Token", equalTo("secret-token"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson(
                        "{\"requestId\":\"" + REQUEST + "\",\"orderId\":3,\"amount\":7.50,\"reason\":\"Return #3\"}")));
    }

    @Test
    void mapsStatusesToOutcomes() {
        SERVER.stubFor(post(urlEqualTo("/internal/v1/payments/refunds")).willReturn(aResponse().withStatus(404)
                .withBody("{\"message\":\"No captured payment\"}")));
        assertThat(client("t").refund(REFUND)).isEqualTo(RefundDelivery.withOutcome(
                RefundDelivery.Outcome.NO_CAPTURED_PAYMENT, "HTTP 404 {\"message\":\"No captured payment\"}"));

        SERVER.stubFor(post(urlEqualTo("/internal/v1/payments/refunds")).willReturn(aResponse().withStatus(500)));
        assertThat(client("t").refund(REFUND)).isEqualTo(RefundDelivery.withOutcome(RefundDelivery.Outcome.RETRY, "HTTP 500"));
    }

    @Test
    void classifiesPermanentAndTransientFailures() {
        assertThat(PaymentServiceClient.classify(HttpStatusCode.valueOf(409), "").outcome())
                .isEqualTo(RefundDelivery.Outcome.REJECTED);
        assertThat(PaymentServiceClient.classify(HttpStatusCode.valueOf(400), null).outcome())
                .isEqualTo(RefundDelivery.Outcome.REJECTED);
        assertThat(PaymentServiceClient.classify(HttpStatusCode.valueOf(422), null).outcome())
                .isEqualTo(RefundDelivery.Outcome.REJECTED);
        assertThat(PaymentServiceClient.classify(HttpStatusCode.valueOf(404), null).outcome())
                .isEqualTo(RefundDelivery.Outcome.NO_CAPTURED_PAYMENT);
        for (int status : new int[] {401, 403, 429, 502, 503}) {
            assertThat(PaymentServiceClient.classify(HttpStatusCode.valueOf(status), null))
                    .isEqualTo(RefundDelivery.withOutcome(RefundDelivery.Outcome.RETRY, "HTTP " + status));
        }
    }

    @Test
    void slowAnswersTimeOutAndAreRetried() {
        SERVER.stubFor(post(urlEqualTo("/internal/v1/payments/refunds"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1500)));

        RefundDelivery delivery = new PaymentServiceClient(SERVER.baseUrl(), "t", Duration.ofMillis(300)).refund(REFUND);

        assertThat(delivery.outcome()).isEqualTo(RefundDelivery.Outcome.RETRY);
    }

    @Test
    void connectionFailuresAreRetried() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        PaymentServiceClient client = new PaymentServiceClient("http://localhost:" + closedPort, null, Duration.ofSeconds(1));

        RefundDelivery delivery = client.refund(REFUND);

        assertThat(delivery.outcome()).isEqualTo(RefundDelivery.Outcome.RETRY);
        assertThat(delivery.detail()).isNotBlank();
    }

    private static PaymentServiceClient client(String token) {
        return new PaymentServiceClient(SERVER.baseUrl(), token, Duration.ofSeconds(2));
    }
}
