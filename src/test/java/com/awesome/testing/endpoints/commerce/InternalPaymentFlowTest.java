package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.payment.PaymentResultResponseDto;
import com.awesome.testing.entity.payment.OrderPaymentStatus;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.RecordedCommerceEvents;
import com.awesome.testing.internal.dto.InternalOrderDto;
import com.awesome.testing.repository.EmailEventRepository;
import com.awesome.testing.repository.payment.OrderPaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.awesome.testing.event.CommerceEventType.ORDER_CANCELLED;
import static com.awesome.testing.event.CommerceEventType.ORDER_CREATED;
import static com.awesome.testing.event.CommerceEventType.ORDER_PAID;
import static com.awesome.testing.event.CommerceEventType.PAYMENT_FAILED;
import static org.assertj.core.api.Assertions.assertThat;

/** {@code POST /internal/v1/orders/{id}/payment}: signed payment results from payment-service. */
class InternalPaymentFlowTest extends AbstractPaymentFlowTest {

    @Autowired
    private RecordedCommerceEvents recorded;

    @org.junit.jupiter.api.BeforeEach
    void forgetEarlierEvents() {
        recorded.clear();
    }

    @Autowired
    private EmailEventRepository emailEventRepository;

    @Autowired
    private OrderPaymentRepository paymentRepository;

    @Autowired
    private com.awesome.testing.repository.event.DomainEventRepository domainEvents;

    @Test
    void capturedPaymentMarksThePendingOrderPaidOnceAndReplaysReturnTheSameAnswer() {
        // given
        OrderDto order = order(clientToken, product("12.50", 5), 2);
        UUID requestId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String body = captured(requestId, paymentId, "25.00");

        // when
        ResponseEntity<PaymentResultResponseDto> first = postResult(order.getId(), body, PaymentResultResponseDto.class);
        ResponseEntity<PaymentResultResponseDto> replay = postResult(order.getId(), body, PaymentResultResponseDto.class);
        ResponseEntity<PaymentResultResponseDto> samePayment = postResult(order.getId(),
                captured(UUID.randomUUID(), paymentId, "25.00"), PaymentResultResponseDto.class);

        // then
        assertThat(List.of(first, replay, samePayment)).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(new PaymentResultResponseDto(order.getId(), OrderStatus.PAID));
        });
        assertThat(publicOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(recorded.typesForOrder(order.getId())).containsExactly(ORDER_CREATED, ORDER_PAID);
        CommerceEvent paid = recorded.forOrder(order.getId()).getLast();
        assertThat(paid.data()).containsEntry("status", "PAID").containsEntry("paymentId", paymentId);
        assertThat(emailsFor(requestId))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getType()).isEqualTo(EmailTemplate.PAYMENT_RECEIVED);
                    assertThat(event.getRecipientEmail()).isEqualTo(client.getEmail());
                    assertThat(event.getPayload()).contains("#" + order.getId()).contains("25.00");
                });
        assertThat(paymentRepository.findByOrderIdOrderByIdAsc(order.getId()))
                .singleElement()
                .satisfies(payment -> {
                    assertThat(payment.getPaymentId()).isEqualTo(paymentId);
                    assertThat(payment.getStatus()).isEqualTo(OrderPaymentStatus.CAPTURED);
                    assertThat(payment.getAmount()).isEqualByComparingTo("25.00");
                    assertThat(payment.getCurrency()).isEqualTo("USD");
                });
    }

    @Test
    void aPaymentResultKeepsTheRequestIdAndTraceOfPaymentServiceOnItsEventAndEmail() {
        // given
        OrderDto order = order(clientToken, product("8.00", 5), 1);
        UUID requestId = UUID.randomUUID();
        String body = captured(requestId, UUID.randomUUID(), "8.00");
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        HttpHeaders headers = signedHeaders(body, Instant.now().getEpochSecond());
        headers.add("X-Request-Id", "pay-result-" + requestId);
        headers.add("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01");

        // when
        ResponseEntity<PaymentResultResponseDto> response = postResult(order.getId(), body, headers,
                PaymentResultResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("pay-result-" + requestId);
        assertThat(domainEvents.findByOrderIdOrderBySequenceAsc(order.getId())).last().satisfies(event -> {
            assertThat(event.getType()).isEqualTo("order.paid");
            assertThat(event.getRequestId()).isEqualTo("pay-result-" + requestId);
            assertThat(event.getTraceId()).isEqualTo(traceId);
        });
        assertThat(emailsFor(requestId)).singleElement().satisfies(email -> {
            assertThat(email.getCorrelationRequestId()).isEqualTo("pay-result-" + requestId);
            assertThat(email.getTraceparent()).matches("00-" + traceId + "-[0-9a-f]{16}-01");
        });
    }

    @Test
    void failedPaymentKeepsTheOrderPendingAndPublishesPaymentFailed() {
        // given
        OrderDto order = order(clientToken, product("10.00", 5), 1);
        UUID requestId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String body = failed(requestId, paymentId, "10.00", "CARD_DECLINED");

        // when
        ResponseEntity<PaymentResultResponseDto> response = postResult(order.getId(), body, PaymentResultResponseDto.class);
        ResponseEntity<PaymentResultResponseDto> replay = postResult(order.getId(), body, PaymentResultResponseDto.class);
        ResponseEntity<PaymentResultResponseDto> laterCapture = postResult(order.getId(),
                captured(UUID.randomUUID(), UUID.randomUUID(), "10.00"), PaymentResultResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new PaymentResultResponseDto(order.getId(), OrderStatus.PENDING));
        assertThat(replay.getBody()).isEqualTo(response.getBody());
        assertThat(laterCapture.getBody()).isEqualTo(new PaymentResultResponseDto(order.getId(), OrderStatus.PAID));
        assertThat(recorded.typesForOrder(order.getId())).containsExactly(ORDER_CREATED, PAYMENT_FAILED, ORDER_PAID);
        CommerceEvent paymentFailed = recorded.forOrder(order.getId()).get(1);
        assertThat(paymentFailed.data())
                .containsEntry("status", "PENDING")
                .containsEntry("paymentId", paymentId)
                .containsEntry("failureCode", "CARD_DECLINED");
        assertThat(emailsFor(requestId))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getType()).isEqualTo(EmailTemplate.PAYMENT_FAILED);
                    assertThat(event.getPayload()).contains("CARD_DECLINED");
                });
    }

    @Test
    void ordersThatAreCancelledOrPaidOtherwiseAreNotPayable() {
        // given
        OrderDto paidByCard = order(clientToken, product("5.00", 10), 1);
        OrderDto cancelled = order(clientToken, product("5.00", 10), 1);
        OrderDto paidByAdmin = order(clientToken, product("5.00", 10), 1);
        postResult(paidByCard.getId(), captured(UUID.randomUUID(), UUID.randomUUID(), "5.00"), PaymentResultResponseDto.class);
        executePost(ORDERS_ENDPOINT + "/" + cancelled.getId() + "/cancel", null, getHeadersWith(clientToken), OrderDto.class);
        markPaid(paidByAdmin.getId());

        // when
        List<ResponseEntity<ErrorDto>> responses = List.of(
                postResult(paidByCard.getId(), captured(UUID.randomUUID(), UUID.randomUUID(), "5.00"), ErrorDto.class),
                postResult(cancelled.getId(), captured(UUID.randomUUID(), UUID.randomUUID(), "5.00"), ErrorDto.class),
                postResult(paidByAdmin.getId(), captured(UUID.randomUUID(), UUID.randomUUID(), "5.00"), ErrorDto.class));

        // then
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().getMessage()).isEqualTo("Order is not payable");
        });
        assertThat(recorded.typesForOrder(cancelled.getId())).containsExactly(ORDER_CREATED, ORDER_CANCELLED);
        assertThat(publicOrder(cancelled.getId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void capturedAmountMustEqualTheAmountDue() {
        // given
        OrderDto order = order(clientToken, product("19.99", 5), 1);

        // when
        ResponseEntity<ErrorDto> tooLittle = postResult(order.getId(),
                captured(UUID.randomUUID(), UUID.randomUUID(), "19.98"), ErrorDto.class);
        ResponseEntity<ErrorDto> tooMuch = postResult(order.getId(),
                captured(UUID.randomUUID(), UUID.randomUUID(), "20.00"), ErrorDto.class);
        ResponseEntity<String> threeDecimals = postResult(order.getId(),
                captured(UUID.randomUUID(), UUID.randomUUID(), "19.990"), String.class);

        // then
        assertThat(tooLittle.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(tooLittle.getBody().getMessage()).isEqualTo("Payment amount 19.98 does not match the amount due 19.99");
        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(threeDecimals.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(publicOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(recorded.typesForOrder(order.getId())).containsExactly(ORDER_CREATED);
    }

    @Test
    void reusingARequestIdForAnotherPaymentIsAConflict() {
        // given
        OrderDto order = order(clientToken, product("3.00", 5), 1);
        UUID requestId = UUID.randomUUID();
        postResult(order.getId(), failed(requestId, UUID.randomUUID(), "3.00", "CARD_DECLINED"), PaymentResultResponseDto.class);

        // when
        ResponseEntity<ErrorDto> reused = postResult(order.getId(),
                captured(requestId, UUID.randomUUID(), "3.00"), ErrorDto.class);

        // then
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reused.getBody().getMessage()).isEqualTo("requestId already used with a different payment result");
        assertThat(publicOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void unknownOrderIsNotFound() {
        // when
        ResponseEntity<ErrorDto> response = postResult(Long.MAX_VALUE,
                captured(UUID.randomUUID(), UUID.randomUUID(), "1.00"), ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Order not found");
    }

    @Test
    void invalidSignaturesAreRejectedWith401() {
        // given
        OrderDto order = order(clientToken, product("7.00", 5), 1);
        String body = captured(UUID.randomUUID(), UUID.randomUUID(), "7.00");
        long now = Instant.now().getEpochSecond();
        HttpHeaders unsigned = serviceHeaders(PAYMENT_TOKEN);
        HttpHeaders wrongSecret = serviceHeaders(PAYMENT_TOKEN);
        wrongSecret.add("X-Payment-Signature",
                com.awesome.testing.internal.security.PaymentSignatureVerifier.sign("other-secret", now, body));
        HttpHeaders tamperedBody = signedHeaders(body.replace("7.00", "0.01"), now);

        // when
        List<ResponseEntity<ErrorDto>> responses = List.of(
                postResult(order.getId(), body, unsigned, ErrorDto.class),
                postResult(order.getId(), body, wrongSecret, ErrorDto.class),
                postResult(order.getId(), body, tamperedBody, ErrorDto.class),
                postResult(order.getId(), body, signedHeaders(body, now - 301), ErrorDto.class),
                postResult(order.getId(), body, signedHeaders(body, now + 400), ErrorDto.class));

        // then
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().getMessage()).isEqualTo("Invalid payment signature");
        });
        assertThat(publicOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void onlyThePaymentTokenMayPostResults() {
        // given
        OrderDto order = order(clientToken, product("7.00", 5), 1);
        String body = captured(UUID.randomUUID(), UUID.randomUUID(), "7.00");
        HttpHeaders warehouse = signedHeaders(body, Instant.now().getEpochSecond());
        warehouse.set("X-Service-Token", WAREHOUSE_TOKEN);
        HttpHeaders jwtOnly = getHeadersWith(adminToken);

        // when
        ResponseEntity<ErrorDto> warehouseToken = postResult(order.getId(), body, warehouse, ErrorDto.class);
        ResponseEntity<ErrorDto> adminJwt = postResult(order.getId(), body, jwtOnly, ErrorDto.class);

        // then
        assertThat(List.of(warehouseToken, adminJwt)).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().getMessage()).isEqualTo("Invalid service token");
        });
    }

    @Test
    void invalidBodiesAreRejectedWith400() {
        // given
        OrderDto order = order(clientToken, product("7.00", 5), 1);
        UUID paymentId = UUID.randomUUID();

        // when
        ResponseEntity<Map<String, String>> missingFields = postSigned(order.getId(), "{\"status\":\"CAPTURED\"}");
        ResponseEntity<Map<String, String>> malformed = postSigned(order.getId(), "{not json");
        ResponseEntity<Map<String, String>> unknownStatus = postSigned(order.getId(),
                body(UUID.randomUUID(), paymentId, "VOIDED", "7.00", null));
        ResponseEntity<Map<String, String>> negative = postSigned(order.getId(),
                captured(UUID.randomUUID(), paymentId, "-1.00"));
        ResponseEntity<Map<String, String>> badCurrency = postSigned(order.getId(),
                captured(UUID.randomUUID(), paymentId, "7.00").replace("\"USD\"", "\"usd\""));
        ResponseEntity<Map<String, String>> failedWithoutCode = postSigned(order.getId(),
                failed(UUID.randomUUID(), paymentId, "7.00", null));

        // then
        assertThat(missingFields.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingFields.getBody()).containsOnly(
                Map.entry("requestId", "requestId is required"),
                Map.entry("paymentId", "paymentId is required"),
                Map.entry("amount", "amount is required"));
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getBody()).containsEntry("body", "Malformed payment result");
        assertThat(unknownStatus.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(negative.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(negative.getBody()).containsEntry("amount", "amount must not be negative");
        assertThat(badCurrency.getBody()).containsEntry("currency", "currency must be a three-letter ISO code");
        assertThat(failedWithoutCode.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(failedWithoutCode.getBody()).containsEntry("message", "failureCode is required for FAILED payments");
        assertThat(publicOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void paymentServiceReadsOrdersWithAmountDueAndTheJwksButNothingElse() {
        // given
        OrderDto order = order(clientToken, product("4.25", 5), 2);
        HttpHeaders payment = serviceHeaders(PAYMENT_TOKEN);

        // when
        ResponseEntity<InternalOrderDto> read = executeGet("/internal/v1/orders/" + order.getId(), payment,
                InternalOrderDto.class);
        ResponseEntity<Map<String, Object>> jwks = executeGet("/internal/v1/jwks.json", payment,
                new ParameterizedTypeReference<>() {});
        ResponseEntity<ErrorDto> products = executeGet("/internal/v1/products", payment, ErrorDto.class);
        ResponseEntity<ErrorDto> list = executeGet("/internal/v1/orders?ids=" + order.getId(), payment, ErrorDto.class);
        ResponseEntity<ErrorDto> cancel = executePost("/internal/v1/orders/" + order.getId() + "/warehouse-cancel",
                null, payment, ErrorDto.class);
        ResponseEntity<InternalOrderDto> warehouseRead = executeGet("/internal/v1/orders/" + order.getId(),
                serviceHeaders(WAREHOUSE_TOKEN), InternalOrderDto.class);

        // then
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().amountDue()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(read.getBody().totalAmount()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(jwks.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jwks.getBody()).containsKey("keys");
        assertThat(List.of(products, list, cancel)).allSatisfy(response ->
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(warehouseRead.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void thePublicOrderBodyGainsNoPaymentFields() {
        // given
        OrderDto order = order(clientToken, product("6.00", 5), 1);
        postResult(order.getId(), captured(UUID.randomUUID(), UUID.randomUUID(), "6.00"), PaymentResultResponseDto.class);

        // when
        ResponseEntity<Map<String, Object>> response = executeGet(ORDERS_ENDPOINT + "/" + order.getId(),
                getHeadersWith(clientToken), new ParameterizedTypeReference<>() {});

        // then
        assertThat(response.getBody()).doesNotContainKeys("amountDue", "paymentId", "payments", "paymentStatus");
        assertThat(response.getBody()).containsEntry("status", "PAID");
    }

    private ResponseEntity<Map<String, String>> postSigned(Long orderId, String rawBody) {
        return executePost("/internal/v1/orders/" + orderId + "/payment", rawBody,
                signedHeaders(rawBody, Instant.now().getEpochSecond()), new ParameterizedTypeReference<>() {});
    }

    private OrderDto publicOrder(Long orderId) {
        return executeGet(ORDERS_ENDPOINT + "/" + orderId, getHeadersWith(adminToken), OrderDto.class).getBody();
    }

    private List<com.awesome.testing.entity.EmailEventEntity> emailsFor(UUID paymentRequestId) {
        UUID emailRequestId = UUID.nameUUIDFromBytes(("payment-result:" + paymentRequestId).getBytes(StandardCharsets.UTF_8));
        return emailEventRepository.findAll().stream()
                .filter(event -> emailRequestId.equals(event.getRequestId()))
                .toList();
    }
}
