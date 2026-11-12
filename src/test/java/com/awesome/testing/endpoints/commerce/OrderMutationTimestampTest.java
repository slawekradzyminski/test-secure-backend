package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.giftcard.GiftCardDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.ProductEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** T-025: an order mutation answers with the state it persisted, including updatedAt. */
class OrderMutationTimestampTest extends AbstractTenderFlowTest {

    @Test
    void cancellingAnOrderAnswersWithThePersistedUpdatedAt() {
        // given
        OrderDto order = order(clientToken, product("19.99", 5), 1);

        // when
        ResponseEntity<OrderDto> cancelled = cancel(order.getId(), clientToken, OrderDto.class);

        // then
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().getUpdatedAt())
                .isAfter(order.getUpdatedAt())
                .isEqualTo(persisted(order.getId()).getUpdatedAt());
    }

    @Test
    void cancellingAPaidGiftCardOrderAnswersWithThePersistedUpdatedAt() {
        // given
        GiftCardDto card = issueGiftCard("10.00");
        ProductEntity product = product("75.50", 5);
        addToCart(clientToken, product, 1);
        applyGiftCard(clientToken, card.getCode(), CartDto.class);
        OrderDto order = checkout(clientToken);
        markPaid(order.getId());

        // when
        ResponseEntity<OrderDto> cancelled = cancel(order.getId(), clientToken, OrderDto.class);

        // then
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().getUpdatedAt()).isEqualTo(persisted(order.getId()).getUpdatedAt());
    }

    @Test
    void anAdminStatusChangeAnswersWithThePersistedUpdatedAt() {
        // given
        OrderDto order = order(clientToken, product("5.00", 5), 1);

        // when
        ResponseEntity<OrderDto> shipped = executePut(ORDERS_ENDPOINT + "/" + order.getId() + "/status",
                OrderStatus.SHIPPED, getHeadersWith(adminToken), OrderDto.class);

        // then
        assertThat(shipped.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(shipped.getBody().getUpdatedAt())
                .isAfter(order.getUpdatedAt())
                .isEqualTo(persisted(order.getId()).getUpdatedAt());
    }

    @Test
    void checkoutAnswersWithThePersistedUpdatedAt() {
        // given
        addToCart(clientToken, product("5.00", 5), 1);

        // when
        OrderDto order = checkout(clientToken);

        // then
        assertThat(order.getUpdatedAt()).isEqualTo(persisted(order.getId()).getUpdatedAt());
    }

    private OrderDto persisted(Long orderId) {
        ResponseEntity<OrderDto> response = executeGet(ORDERS_ENDPOINT + "/" + orderId, getHeadersWith(adminToken), OrderDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
