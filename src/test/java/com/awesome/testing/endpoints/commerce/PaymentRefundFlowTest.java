package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.payment.PaymentResultResponseDto;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.entity.payment.CardRefundRequestEntity;
import com.awesome.testing.internal.dto.FulfilmentUpdateDto;
import com.awesome.testing.internal.dto.InternalOrderDto;
import com.awesome.testing.internal.dto.OrderRefundDto;
import com.awesome.testing.repository.event.DomainEventRepository;
import com.awesome.testing.repository.payment.CardRefundRequestRepository;
import com.awesome.testing.service.payment.OrderCancelledListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booked refunds and cancellations of card-paid orders request the card refund through a
 * {@code card-refund.requested} feed event; the backend never calls payment-service.
 */
class PaymentRefundFlowTest extends AbstractPaymentFlowTest {

    private static final String CARD_REFUND = "card-refund.requested";

    @Autowired
    private CardRefundRequestRepository cardRefundRepository;

    @Autowired
    private DomainEventRepository eventRepository;

    @Test
    void aCardPaidRefundIsRequestedThroughTheFeed() {
        // given
        Long orderId = deliveredCardOrder("30.00");
        long before = eventRepository.maxSequence();
        OrderRefundDto refund = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("12.50"), "Return #7");

        // when
        ResponseEntity<InternalOrderDto> booked = bookRefund(orderId, refund);
        ResponseEntity<String> feed = executeGet("/internal/v1/events?after=" + before + "&types=" + CARD_REFUND,
                serviceHeaders(PAYMENT_TOKEN), String.class);

        // then
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.OK);
        DomainEventEntity event = cardRefundEvents(orderId).getFirst();
        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(feed.getBody()).contains("\"type\":\"card-refund.requested\"")
                .contains("\"data\":{\"amount\":12.50,\"orderId\":" + orderId + ",\"reason\":\"Return #7\",\"requestId\":\""
                        + refund.requestId() + "\"}")
                .contains("\"id\":\"" + event.getEventId() + "\"");
        assertThat(cardRefundEvents(orderId)).singleElement();
        assertThat(ledger(orderId)).singleElement().satisfies(row -> {
            assertThat(row.getRequestId()).isEqualTo(refund.requestId());
            assertThat(row.getAmount()).isEqualByComparingTo("12.50");
            assertThat(row.getReason()).isEqualTo("Return #7");
        });
    }

    @Test
    void eachNewRefundRequestsOneCardRefundAndAdminPaidOrdersSkipTheCard() {
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
        assertThat(ledger(cardOrder)).extracting(CardRefundRequestEntity::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("15.00"), new BigDecimal("5.00"));
        assertThat(cardRefundEvents(cardOrder)).extracting(DomainEventEntity::getPayload).containsExactly(
                cardRefundPayload("15.00", cardOrder, "Return #11", first.requestId()),
                cardRefundPayload("5.00", cardOrder, "Return #12", second.requestId()));
        assertThat(manualRefund.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ledger(adminPaid.getId())).isEmpty();
        assertThat(cardRefundEvents(adminPaid.getId())).isEmpty();
    }

    @Test
    void customerCancelOfACardPaidOrderRequestsTheCardRefundOnce() {
        // given
        Long orderId = paidCardOrder("18.00");
        UUID requestId = OrderCancelledListener.requestId(orderId);

        // when
        ResponseEntity<OrderDto> cancelled = cancel(orderId, clientToken);
        ResponseEntity<String> again = executePost(ORDERS_ENDPOINT + "/" + orderId + "/cancel", null,
                getHeadersWith(clientToken), String.class);

        // then
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ledger(orderId)).singleElement().satisfies(row -> {
            assertThat(row.getRequestId()).isEqualTo(requestId);
            assertThat(row.getAmount()).isEqualByComparingTo("18.00");
            assertThat(row.getReason()).isEqualTo("Order cancelled");
        });
        assertThat(cardRefundEvents(orderId)).singleElement().satisfies(event -> {
            assertThat(event.getPayload()).isEqualTo(cardRefundPayload("18.00", orderId, "Order cancelled", requestId));
            assertThat(event.getUsername()).isEqualTo(client.getUsername());
        });
        assertThat(eventRepository.findByOrderIdOrderBySequenceAsc(orderId)).extracting(DomainEventEntity::getType)
                .containsExactly("order.created", "order.paid", "order.cancelled", CARD_REFUND);
    }

    @Test
    void adminCancelOfACardPaidOrderRequestsTheCardRefund() {
        // given
        Long orderId = paidCardOrder("9.99");

        // when
        ResponseEntity<OrderDto> cancelled = executePut(ORDERS_ENDPOINT + "/" + orderId + "/status",
                OrderStatus.CANCELLED, getHeadersWith(adminToken), OrderDto.class);

        // then
        assertThat(cancelled.getBody().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cardRefundEvents(orderId)).singleElement().extracting(DomainEventEntity::getPayload)
                .isEqualTo(cardRefundPayload("9.99", orderId, "Order cancelled", OrderCancelledListener.requestId(orderId)));
    }

    @Test
    void warehouseCancelOfACardPaidOrderRequestsTheCardRefund() {
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
        assertThat(ledger(orderId)).singleElement().extracting(CardRefundRequestEntity::getAmount)
                .satisfies(amount -> assertThat(amount).isEqualByComparingTo("12.00"));
        assertThat(cardRefundEvents(orderId)).singleElement();
    }

    @Test
    void cancellingAdminPaidOrUnpaidOrdersRequestsNothing() {
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
        for (Long orderId : List.of(adminPaid.getId(), unpaid.getId(), failedOnly.getId())) {
            assertThat(ledger(orderId)).isEmpty();
            assertThat(cardRefundEvents(orderId)).isEmpty();
            assertThat(eventRepository.findByOrderIdOrderBySequenceAsc(orderId)).extracting(DomainEventEntity::getType)
                    .contains("order.cancelled");
        }
    }

    private static String cardRefundPayload(String amount, Long orderId, String reason, UUID requestId) {
        return "{\"amount\":" + amount + ",\"orderId\":" + orderId + ",\"reason\":\"" + reason
                + "\",\"requestId\":\"" + requestId + "\"}";
    }

    private List<CardRefundRequestEntity> ledger(Long orderId) {
        return cardRefundRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    private List<DomainEventEntity> cardRefundEvents(Long orderId) {
        return eventRepository.findByOrderIdOrderBySequenceAsc(orderId).stream()
                .filter(event -> CARD_REFUND.equals(event.getType()))
                .toList();
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
        Long orderId = paidCardOrder(price);
        ResponseEntity<OrderDto> delivered = executePut(ORDERS_ENDPOINT + "/" + orderId + "/status",
                OrderStatus.DELIVERED, getHeadersWith(adminToken), OrderDto.class);
        assertThat(delivered.getStatusCode()).isEqualTo(HttpStatus.OK);
        return orderId;
    }

    private ResponseEntity<InternalOrderDto> bookRefund(Long orderId, OrderRefundDto refund) {
        return executePost("/internal/v1/orders/" + orderId + "/refunds", refund, serviceHeaders(WAREHOUSE_TOKEN),
                InternalOrderDto.class);
    }
}
