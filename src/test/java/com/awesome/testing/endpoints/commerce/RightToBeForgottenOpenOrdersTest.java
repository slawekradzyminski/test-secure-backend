package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.payment.PaymentResultResponseDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.repository.event.DomainEventRepository;
import com.awesome.testing.service.payment.OrderCancelledListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Erasing an account (right to be forgotten) cancels its open orders through the normal
 * cancellation path before the order rows are deleted: reserved stock goes back to the
 * catalogue and card payments are refunded through the event feed.
 */
class RightToBeForgottenOpenOrdersTest extends AbstractPaymentFlowTest {

    @Autowired
    private DomainEventRepository eventRepository;

    @Test
    void erasureRestoresTheStockOfPendingAndPaidOrdersButNotOfDeliveredOnes() {
        // given
        ProductEntity pendingProduct = product("10.00", 5);
        ProductEntity paidProduct = product("10.00", 5);
        ProductEntity deliveredProduct = product("10.00", 5);
        OrderDto pending = order(clientToken, pendingProduct, 2);
        OrderDto paid = order(clientToken, paidProduct, 3);
        markPaid(paid.getId());
        OrderDto delivered = order(clientToken, deliveredProduct, 1);
        deliver(delivered.getId());
        assertThat(stockOf(pendingProduct)).isEqualTo(3);

        // when
        ResponseEntity<String> response = forget();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(stockOf(pendingProduct)).isEqualTo(5);
        assertThat(stockOf(paidProduct)).isEqualTo(5);
        assertThat(stockOf(deliveredProduct)).isEqualTo(4);
        assertThat(orderRepository.countByUsername(client.getUsername())).isZero();
        assertThat(eventTypes(pending.getId())).containsExactly("order.created", "order.cancelled");
        assertThat(eventTypes(paid.getId())).containsExactly("order.created", "order.paid", "order.cancelled");
        assertThat(eventTypes(delivered.getId())).doesNotContain("order.cancelled");
    }

    @Test
    void erasureOfACardPaidOrderRequestsTheCardRefundThroughTheFeed() {
        // given
        OrderDto order = order(clientToken, product("18.00", 5), 1);
        ResponseEntity<PaymentResultResponseDto> paid = postResult(order.getId(),
                captured(UUID.randomUUID(), UUID.randomUUID(), "18.00"), PaymentResultResponseDto.class);
        assertThat(paid.getBody().orderStatus()).isEqualTo(OrderStatus.PAID);

        // when
        ResponseEntity<String> response = forget();

        // then: the feed event outlives the deleted order, so payment-service still gets the refund
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(orderRepository.findById(order.getId())).isEmpty();
        assertThat(eventRepository.findByOrderIdOrderBySequenceAsc(order.getId()))
                .filteredOn(event -> "card-refund.requested".equals(event.getType()))
                .singleElement()
                .extracting(DomainEventEntity::getPayload)
                .isEqualTo("{\"amount\":18.00,\"orderId\":" + order.getId() + ",\"reason\":\"Order cancelled\","
                        + "\"requestId\":\"" + OrderCancelledListener.requestId(order.getId()) + "\"}");
    }

    private ResponseEntity<String> forget() {
        return executeDelete(getUserEndpoint(client.getUsername()) + "/right-to-be-forgotten",
                getHeadersWith(clientToken), String.class);
    }

    private List<String> eventTypes(Long orderId) {
        return eventRepository.findByOrderIdOrderBySequenceAsc(orderId).stream()
                .map(DomainEventEntity::getType)
                .toList();
    }
}
