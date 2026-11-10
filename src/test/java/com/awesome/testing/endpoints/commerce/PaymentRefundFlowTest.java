package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.payment.PaymentResultResponseDto;
import com.awesome.testing.entity.payment.PaymentRefundOutboxEntity;
import com.awesome.testing.entity.payment.PaymentRefundStatus;
import com.awesome.testing.internal.dto.InternalOrderDto;
import com.awesome.testing.internal.dto.OrderRefundDto;
import com.awesome.testing.repository.payment.PaymentRefundOutboxRepository;
import com.awesome.testing.service.payment.PaymentRefundJob;
import com.awesome.testing.service.payment.OrderCancelledListener;
import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.internal.dto.FulfilmentUpdateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.awesome.testing.service.payment.PaymentRefundOutbox;
import com.awesome.testing.service.payment.PaymentServiceClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/** Booked refunds of card-paid orders are queued and delivered to payment-service. */
class PaymentRefundFlowTest extends AbstractPaymentFlowTest {

    private static final String REFUNDS = "/internal/v1/payments/refunds";

    @Autowired
    private PaymentRefundOutboxRepository outboxRepository;

    @Autowired
    private PaymentRefundOutbox outbox;

    /**
     * A standalone server instead of {@code @EnableWireMock}: a second Spring context would
     * recreate the shared H2 schema and reset order ids under the other commerce tests.
     */
    private static final WireMockServer PAYMENT_SERVICE = new WireMockServer(options().dynamicPort());

    private PaymentRefundJob refundJob;

    @BeforeAll
    static void startPaymentService() {
        PAYMENT_SERVICE.start();
    }

    @AfterAll
    static void stopPaymentService() {
        PAYMENT_SERVICE.stop();
    }

    @BeforeEach
    void clearOutbox() {
        outboxRepository.deleteAll();
        PAYMENT_SERVICE.resetAll();
        refundJob = new PaymentRefundJob(outbox,
                new PaymentServiceClient(PAYMENT_SERVICE.baseUrl(), PAYMENT_TOKEN, Duration.ofSeconds(2)));
    }

    @Test
    void cardPaidRefundIsDeliveredWithThePaymentToken() {
        // given
        Long orderId = deliveredCardOrder("30.00");
        UUID refundId = UUID.randomUUID();
        OrderRefundDto refund = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("12.50"), "Return #7");
        PAYMENT_SERVICE.stubFor(post(urlEqualTo(REFUNDS)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"refundId":"%s","paymentId":"%s","amount":12.50,"status":"PARTIALLY_REFUNDED"}"""
                        .formatted(refundId, UUID.randomUUID()))));

        // when
        assertThat(bookRefund(orderId, refund).getStatusCode()).isEqualTo(HttpStatus.OK);
        int delivered = refundJob.deliverDue();

        // then
        assertThat(delivered).isEqualTo(1);
        PAYMENT_SERVICE.verify(exactly(1), postRequestedFor(urlEqualTo(REFUNDS))
                .withHeader("X-Service-Token", equalTo(PAYMENT_TOKEN))
                .withRequestBody(equalToJson("""
                        {"requestId":"%s","orderId":%d,"amount":12.50,"reason":"Return #7"}"""
                        .formatted(refund.requestId(), orderId))));
        assertThat(outbox(refund.requestId())).satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo(PaymentRefundStatus.SENT);
            assertThat(row.getRefundId()).isEqualTo(refundId);
            assertThat(row.getAttempts()).isEqualTo(1);
            assertThat(row.getNextAttemptAt()).isNull();
        });
        assertThat(refundJob.deliverDue()).isZero();
    }

    @Test
    void notFoundMeansNothingToRefundOnTheCard() {
        // given
        Long orderId = deliveredCardOrder("10.00");
        OrderRefundDto refund = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("10.00"), "Return #8");
        PAYMENT_SERVICE.stubFor(post(urlEqualTo(REFUNDS)).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json").withBody("{\"message\":\"No captured payment\"}")));

        // when
        bookRefund(orderId, refund);
        refundJob.deliverDue();

        // then
        assertThat(outbox(refund.requestId()).getStatus()).isEqualTo(PaymentRefundStatus.SKIPPED);
        assertThat(refundJob.deliverDue()).isZero();
    }

    @Test
    void conflictMarksTheRefundFailedWithoutRetrying() {
        // given
        Long orderId = deliveredCardOrder("10.00");
        OrderRefundDto refund = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("4.00"), "Return #9");
        PAYMENT_SERVICE.stubFor(post(urlEqualTo(REFUNDS)).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json").withBody("{\"message\":\"Refund exceeds refundable\"}")));

        // when
        bookRefund(orderId, refund);
        refundJob.deliverDue();

        // then
        PaymentRefundOutboxEntity row = outbox(refund.requestId());
        assertThat(row.getStatus()).isEqualTo(PaymentRefundStatus.FAILED);
        assertThat(row.getLastError()).contains("409").contains("Refund exceeds refundable");
        assertThat(row.getNextAttemptAt()).isNull();
    }

    @Test
    void serverErrorsAreRetriedWithBackoff() {
        // given
        Long orderId = deliveredCardOrder("10.00");
        OrderRefundDto refund = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("4.00"), "Return #10");
        PAYMENT_SERVICE.stubFor(post(urlEqualTo(REFUNDS)).willReturn(aResponse().withStatus(503)));
        Instant before = Instant.now();

        // when
        bookRefund(orderId, refund);
        refundJob.deliverDue();

        // then
        PaymentRefundOutboxEntity row = outbox(refund.requestId());
        assertThat(row.getStatus()).isEqualTo(PaymentRefundStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).startsWith("HTTP 503");
        assertThat(row.getNextAttemptAt()).isBetween(before.plus(Duration.ofSeconds(29)), Instant.now().plus(Duration.ofSeconds(31)));
        assertThat(refundJob.deliverDue()).isZero();
    }

    @Test
    void eachNewRefundQueuesOneCardRefundAndAdminPaidOrdersSkipTheCard() {
        // given
        Long cardOrder = deliveredCardOrder("20.00");
        OrderDto adminPaid = order(clientToken, product("20.00", 5), 1);
        deliver(adminPaid.getId());
        OrderRefundDto first = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("15.00"), "Return #11");
        OrderRefundDto second = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("5.00"), "Return #12");
        OrderRefundDto manual = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("20.00"), "Return #13");

        // when
        bookRefund(cardOrder, first);
        bookRefund(cardOrder, second);
        bookRefund(cardOrder, first);
        ResponseEntity<InternalOrderDto> manualRefund = bookRefund(adminPaid.getId(), manual);

        // then
        assertThat(outboxRepository.findByOrderIdOrderByIdAsc(cardOrder))
                .extracting(PaymentRefundOutboxEntity::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("15.00"), new BigDecimal("5.00"));
        assertThat(manualRefund.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(outboxRepository.findByOrderIdOrderByIdAsc(adminPaid.getId())).isEmpty();
    }

    @Test
    void customerCancelOfACardPaidOrderRefundsTheCardOnce() {
        // given
        Long orderId = paidCardOrder("18.00");
        PAYMENT_SERVICE.stubFor(post(urlEqualTo(REFUNDS)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"refundId\":\"" + UUID.randomUUID() + "\"}")));

        // when
        ResponseEntity<OrderDto> cancelled = cancel(orderId, clientToken);
        ResponseEntity<String> again = executePost(ORDERS_ENDPOINT + "/" + orderId + "/cancel", null,
                getHeadersWith(clientToken), String.class);
        refundJob.deliverDue();

        // then
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(outboxRepository.findByOrderIdOrderByIdAsc(orderId)).singleElement().satisfies(row -> {
            assertThat(row.getRequestId()).isEqualTo(OrderCancelledListener.requestId(orderId));
            assertThat(row.getAmount()).isEqualByComparingTo("18.00");
            assertThat(row.getReason()).isEqualTo("Order cancelled");
            assertThat(row.getStatus()).isEqualTo(PaymentRefundStatus.SENT);
        });
        PAYMENT_SERVICE.verify(exactly(1), postRequestedFor(urlEqualTo(REFUNDS))
                .withRequestBody(equalToJson("""
                        {"requestId":"%s","orderId":%d,"amount":18.00,"reason":"Order cancelled"}"""
                        .formatted(OrderCancelledListener.requestId(orderId), orderId))));
    }

    @Test
    void adminCancelOfACardPaidOrderRefundsTheCard() {
        // given
        Long orderId = paidCardOrder("9.99");

        // when
        ResponseEntity<OrderDto> cancelled = executePut(ORDERS_ENDPOINT + "/" + orderId + "/status",
                OrderStatus.CANCELLED, getHeadersWith(adminToken), OrderDto.class);

        // then
        assertThat(cancelled.getBody().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outbox(OrderCancelledListener.requestId(orderId)).getAmount()).isEqualByComparingTo("9.99");
    }

    @Test
    void warehouseCancelOfACardPaidOrderRefundsTheCard() {
        // given
        Long orderId = paidCardOrder("12.00");
        assertThat(executePut("/internal/v1/orders/" + orderId + "/fulfilment",
                new FulfilmentUpdateDto(FulfilmentState.AWAITING_PICK), serviceHeaders(WAREHOUSE_TOKEN),
                InternalOrderDto.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // when
        ResponseEntity<InternalOrderDto> cancelled = executePost("/internal/v1/orders/" + orderId + "/warehouse-cancel",
                null, serviceHeaders(WAREHOUSE_TOKEN), InternalOrderDto.class);

        // then
        assertThat(cancelled.getBody().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outbox(OrderCancelledListener.requestId(orderId)).getAmount()).isEqualByComparingTo("12.00");
    }

    @Test
    void cancellingAdminPaidOrUnpaidOrdersRefundsNothing() {
        // given
        OrderDto adminPaid = order(clientToken, product("5.00", 5), 1);
        OrderDto unpaid = order(clientToken, product("5.00", 5), 1);
        OrderDto failedOnly = order(clientToken, product("5.00", 5), 1);
        markPaid(adminPaid.getId());
        postResult(failedOnly.getId(), failed(UUID.randomUUID(), UUID.randomUUID(), "5.00", "CARD_DECLINED"),
                PaymentResultResponseDto.class);

        // when
        cancel(adminPaid.getId(), adminToken);
        cancel(unpaid.getId(), clientToken);
        cancel(failedOnly.getId(), clientToken);

        // then
        assertThat(outboxRepository.findAll()).isEmpty();
    }

    private Long paidCardOrder(String price) {
        OrderDto order = order(clientToken, product(price, 5), 1);
        ResponseEntity<PaymentResultResponseDto> paid = postResult(order.getId(),
                captured(UUID.randomUUID(), UUID.randomUUID(), price), PaymentResultResponseDto.class);
        assertThat(paid.getBody().orderStatus()).isEqualTo(OrderStatus.PAID);
        return order.getId();
    }

    private ResponseEntity<OrderDto> cancel(Long orderId, String token) {
        return executePost(ORDERS_ENDPOINT + "/" + orderId + "/cancel", null, getHeadersWith(token), OrderDto.class);
    }

    private Long deliveredCardOrder(String price) {
        OrderDto order = order(clientToken, product(price, 5), 1);
        ResponseEntity<PaymentResultResponseDto> paid = postResult(order.getId(),
                captured(UUID.randomUUID(), UUID.randomUUID(), price), PaymentResultResponseDto.class);
        assertThat(paid.getBody().orderStatus()).isEqualTo(OrderStatus.PAID);
        ResponseEntity<OrderDto> delivered = executePut(ORDERS_ENDPOINT + "/" + order.getId() + "/status",
                OrderStatus.DELIVERED, getHeadersWith(adminToken), OrderDto.class);
        assertThat(delivered.getStatusCode()).isEqualTo(HttpStatus.OK);
        return order.getId();
    }

    private ResponseEntity<InternalOrderDto> bookRefund(Long orderId, OrderRefundDto refund) {
        return executePost("/internal/v1/orders/" + orderId + "/refunds", refund, serviceHeaders(WAREHOUSE_TOKEN),
                InternalOrderDto.class);
    }

    private PaymentRefundOutboxEntity outbox(UUID requestId) {
        return outboxRepository.findByRequestId(requestId).orElseThrow();
    }
}
