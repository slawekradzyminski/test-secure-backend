package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.giftcard.GiftCardDto;
import com.awesome.testing.dto.loyalty.LoyaltyTransactionDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.entity.loyalty.LoyaltyTransactionType;
import com.awesome.testing.entity.payment.PaymentRefundOutboxEntity;
import com.awesome.testing.internal.dto.OrderRefundDto;
import com.awesome.testing.repository.payment.PaymentRefundOutboxRepository;
import com.awesome.testing.service.payment.OrderCancelledListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Orders paid partly by card, partly by gift card and points, cancelled or refunded. */
class TenderCardPaymentFlowTest extends AbstractTenderFlowTest {

    private static final ParameterizedTypeReference<PageDto<LoyaltyTransactionDto>> LEDGER = new ParameterizedTypeReference<>() {};

    @Autowired
    private PaymentRefundOutboxRepository outboxRepository;

    private GiftCardDto card;

    @BeforeEach
    void clearOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    void cancellingAMixedPaidOrderRefundsTheCardRestoresTendersAndRevokesPointsOnce() {
        OrderDto order = cardPaidOrder();
        assertThat(loyalty(clientToken).getBalance()).isEqualTo(100);

        ResponseEntity<OrderDto> cancelled = cancel(order.getId(), clientToken, OrderDto.class);
        ResponseEntity<ErrorDto> again = cancel(order.getId(), clientToken, ErrorDto.class);

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(outboxRepository.findByOrderIdOrderByIdAsc(order.getId())).singleElement().satisfies(row -> {
            assertThat(row.getAmount()).isEqualByComparingTo("70.00");
            assertThat(row.getRequestId()).isEqualTo(OrderCancelledListener.requestId(order.getId()));
        });
        assertThat(balanceOf(card.getCode())).isEqualByComparingTo("20.00");
        assertThat(loyalty(clientToken).getBalance()).isEqualTo(1_000);
        assertThat(ledger()).filteredOn(entry -> entry.getType() == LoyaltyTransactionType.REVOKED)
                .singleElement().satisfies(entry -> assertThat(entry.getPoints()).isEqualTo(-100));
        assertThat(ledger()).filteredOn(entry -> entry.getType() == LoyaltyTransactionType.RESTORED)
                .singleElement().satisfies(entry -> assertThat(entry.getPoints()).isEqualTo(1_000));
    }

    @Test
    void refundsOfAMixedPaidOrderGoToTheCardThenGiftCardThenPoints() {
        OrderDto order = cardPaidOrder();
        executePut(ORDERS_ENDPOINT + "/" + order.getId() + "/status", OrderStatus.DELIVERED,
                getHeadersWith(adminToken), OrderDto.class);
        OrderRefundDto first = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("80.00"), "Return #1");
        OrderRefundDto second = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("20.00"), "Return #2");

        refund(order.getId(), first, String.class);
        BigDecimal giftCardAfterFirst = balanceOf(card.getCode());
        refund(order.getId(), second, String.class);

        List<PaymentRefundOutboxEntity> cardRefunds = outboxRepository.findByOrderIdOrderByIdAsc(order.getId());
        assertThat(cardRefunds).singleElement().satisfies(row -> {
            assertThat(row.getAmount()).isEqualByComparingTo("70.00");
            assertThat(row.getRequestId()).isEqualTo(first.requestId());
        });
        assertThat(giftCardAfterFirst).isEqualByComparingTo("10.00");
        assertThat(balanceOf(card.getCode())).isEqualByComparingTo("20.00");
        assertThat(loyalty(clientToken).getBalance()).isEqualTo(1_000);
    }

    /** 100.00 order: gift card 20.00, 1000 points (10.00), 70.00 captured on the card. */
    private OrderDto cardPaidOrder() {
        card = issueGiftCard("20.00");
        grantPoints(client.getUsername(), 1_000);
        addToCart(clientToken, product("100.00", 5), 1);
        applyGiftCard(clientToken, card.getCode(), CartDto.class);
        applyPoints(clientToken, 1_000, CartDto.class);
        OrderDto order = checkout(clientToken);
        assertThat(order.getAmountDue()).isEqualByComparingTo("70.00");
        ResponseEntity<String> paid = postResult(order.getId(), captured(UUID.randomUUID(), UUID.randomUUID(), "70.00"), String.class);
        assertThat(paid.getStatusCode()).as(paid.getBody()).isEqualTo(HttpStatus.OK);
        return order;
    }

    private List<LoyaltyTransactionDto> ledger() {
        return executeGet(LOYALTY + "/transactions?size=100", getHeadersWith(clientToken), LEDGER).getBody().getContent();
    }
}
