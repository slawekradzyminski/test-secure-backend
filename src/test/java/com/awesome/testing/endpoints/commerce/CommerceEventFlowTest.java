package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.RecordedCommerceEvents;
import com.awesome.testing.internal.dto.FulfilmentUpdateDto;
import com.awesome.testing.internal.dto.InternalOrderDto;
import com.awesome.testing.internal.dto.OrderRefundDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static com.awesome.testing.event.CommerceEventType.ORDER_CANCELLED;
import static com.awesome.testing.event.CommerceEventType.ORDER_CREATED;
import static com.awesome.testing.event.CommerceEventType.ORDER_DELIVERED;
import static com.awesome.testing.event.CommerceEventType.ORDER_PAID;
import static com.awesome.testing.event.CommerceEventType.ORDER_SHIPPED;
import static com.awesome.testing.event.CommerceEventType.REFUND_BOOKED;
import static org.assertj.core.api.Assertions.assertThat;

/** Commerce domain events reach AFTER_COMMIT listeners once per real state change. */
class CommerceEventFlowTest extends AbstractCommerceFlowTest {

    @Autowired
    private RecordedCommerceEvents recorded;

    @org.junit.jupiter.api.BeforeEach
    void forgetEarlierEvents() {
        recorded.clear();
    }

    @Test
    void theWarehouseLifecyclePublishesEachStepOnce() {
        OrderDto order = order(clientToken, product("20.00", 5), 2);
        Long id = order.getId();

        markPaid(id);
        markPaid(id);
        for (FulfilmentState state : new FulfilmentState[] {FulfilmentState.AWAITING_PICK, FulfilmentState.PICKED,
                FulfilmentState.PACKED, FulfilmentState.SHIPPED, FulfilmentState.SHIPPED, FulfilmentState.DELIVERED}) {
            assertThat(fulfilment(id, state).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        OrderRefundDto refund = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("15.00"), "Return #1");
        assertThat(refund(id, refund).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refund(id, refund).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(recorded.typesForOrder(id))
                .containsExactly(ORDER_CREATED, ORDER_PAID, ORDER_SHIPPED, ORDER_DELIVERED, REFUND_BOOKED);
        CommerceEvent created = recorded.forOrder(id).getFirst();
        assertThat(created.username()).isEqualTo(client.getUsername());
        assertThat(created.id()).isNotNull();
        assertThat(created.occurredAt()).isNotNull();
        assertThat(created.data()).containsEntry("orderId", id).containsEntry("status", "PENDING");
        assertThat((BigDecimal) created.data().get("totalAmount")).isEqualByComparingTo("40.00");
        CommerceEvent booked = recorded.forOrder(id).getLast();
        assertThat(booked.data())
                .containsEntry("status", "DELIVERED")
                .containsEntry("amount", new BigDecimal("15.00"))
                .containsEntry("requestId", refund.requestId());
    }

    @Test
    void cancellationsAndLegacyStatusChangesPublishAndRejectedChangesDoNot() {
        OrderDto cancelled = order(clientToken, product("10.00", 5), 1);
        OrderDto delivered = order(clientToken, product("10.00", 5), 1);

        ResponseEntity<OrderDto> cancel = cancel(cancelled.getId(), clientToken, OrderDto.class);
        ResponseEntity<ErrorDto> cancelAgain = cancel(cancelled.getId(), clientToken, ErrorDto.class);
        ResponseEntity<ErrorDto> foreignCancel = cancel(delivered.getId(), otherClientToken, ErrorDto.class);
        deliver(delivered.getId());
        ResponseEntity<ErrorDto> emptyCheckout = executePost(ORDERS_ENDPOINT,
                getRandomAddress(), getHeadersWith(clientToken), ErrorDto.class);

        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelAgain.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(foreignCancel.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(emptyCheckout.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(recorded.typesForOrder(cancelled.getId())).containsExactly(ORDER_CREATED, ORDER_CANCELLED);
        assertThat(recorded.forOrder(cancelled.getId()).getLast().data()).containsEntry("status", OrderStatus.CANCELLED.name());
        assertThat(recorded.typesForOrder(delivered.getId())).containsExactly(ORDER_CREATED, ORDER_PAID, ORDER_DELIVERED);
    }

    private ResponseEntity<InternalOrderDto> fulfilment(Long orderId, FulfilmentState state) {
        return executePut("/internal/v1/orders/" + orderId + "/fulfilment", new FulfilmentUpdateDto(state),
                serviceHeaders(), InternalOrderDto.class);
    }

    private ResponseEntity<InternalOrderDto> refund(Long orderId, OrderRefundDto request) {
        return executePost("/internal/v1/orders/" + orderId + "/refunds", request, serviceHeaders(), InternalOrderDto.class);
    }

    private <T> ResponseEntity<T> cancel(Long orderId, String token, Class<T> type) {
        return executePost(ORDERS_ENDPOINT + "/" + orderId + "/cancel", null, getHeadersWith(token), type);
    }

    private HttpHeaders serviceHeaders() {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Service-Token", "test-warehouse-token");
        return headers;
    }
}
