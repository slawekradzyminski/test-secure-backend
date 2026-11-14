package com.awesome.testing.graphql;

import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.entity.OrderEntity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code Order.reservationExpiresAt} is the stored reservation end, and null for orders without one. */
class OrderReservationGraphQlTest extends AbstractCommerceGraphQlTest {

    private static final String ORDER = "query($id: ID!) { order(id: $id) { id reservationExpiresAt } }";

    @Test
    void orderReadsAndCheckoutReturnTheRestValue() {
        Actor customer = customer();
        OrderDto order = givenOrder(customer, product("10.00", 5));

        JsonNode single = successful(customer, ORDER, Map.of("id", order.getId())).path("order");
        JsonNode listed = successful(customer, "{ orders { items { id reservationExpiresAt } } }", Map.of())
                .at("/orders/items/0");

        assertThat(order.getReservationExpiresAt()).isNotNull();
        assertThat(single.path("reservationExpiresAt").asString()).isEqualTo(order.getReservationExpiresAt());
        assertThat(listed.path("reservationExpiresAt").asString()).isEqualTo(order.getReservationExpiresAt());
    }

    @Test
    void ordersWithoutAReservationAnswerNull() {
        Actor customer = customer();
        OrderDto order = givenOrder(customer, product("10.00", 5));
        OrderEntity entity = orderRepository.findById(order.getId()).orElseThrow();
        entity.setReservationExpiresAt(null);
        orderRepository.save(entity);

        JsonNode single = successful(customer, ORDER, Map.of("id", order.getId())).path("order");

        assertThat(single.path("reservationExpiresAt").isNull()).isTrue();
    }
}
