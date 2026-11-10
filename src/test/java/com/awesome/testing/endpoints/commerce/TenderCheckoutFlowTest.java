package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.coupon.ApplyCouponDto;
import com.awesome.testing.dto.coupon.CouponRequestDto;
import com.awesome.testing.entity.coupon.CouponType;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.giftcard.GiftCardDto;
import com.awesome.testing.dto.loyalty.LoyaltyTransactionDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.giftcard.GiftCardTransactionType;
import com.awesome.testing.entity.loyalty.LoyaltyTransactionType;
import com.awesome.testing.event.RecordedCommerceEvents;
import com.awesome.testing.internal.dto.OrderRefundDto;
import com.awesome.testing.repository.EmailEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.awesome.testing.event.CommerceEventType.ORDER_CREATED;
import static com.awesome.testing.event.CommerceEventType.ORDER_PAID;
import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static org.assertj.core.api.Assertions.assertThat;

/** Checkout, payment, cancellation and refunds with gift cards and loyalty points. */
class TenderCheckoutFlowTest extends AbstractTenderFlowTest {

    private static final ParameterizedTypeReference<PageDto<LoyaltyTransactionDto>> LEDGER = new ParameterizedTypeReference<>() {};

    @Autowired
    private RecordedCommerceEvents recorded;

    @Autowired
    private EmailEventRepository emailEventRepository;

    @Test
    void ordersWithoutTendersKeepTheirBodyAndAmountDue() {
        OrderDto order = order(clientToken, product("10.00", 5), 1);

        assertThat(order.getAmountDue()).isNull();
        assertThat(order.getGiftCardCode()).isNull();
        assertThat(order.getPointsRedeemed()).isNull();
    }

    @Test
    void checkoutWithAGiftCardDebitsItAndKeepsTheOrderPending() {
        GiftCardDto card = issueGiftCard("30.00");
        addToCart(clientToken, product("25.00", 5), 2);
        applyGiftCard(clientToken, card.getCode(), CartDto.class);

        OrderDto created = checkout(clientToken);
        OrderDto fetched = executeGet(ORDERS_ENDPOINT + "/" + created.getId(), getHeadersWith(clientToken), OrderDto.class).getBody();

        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(created.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(created.getGiftCardCode()).isEqualTo(card.getCode());
        assertThat(created.getGiftCardAmount()).isEqualByComparingTo("30.00");
        assertThat(created.getPointsRedeemed()).isNull();
        assertThat(created.getAmountDue()).isEqualByComparingTo("20.00");
        assertThat(fetched).isEqualTo(created);
        assertThat(balanceOf(card.getCode())).isEqualByComparingTo("0.00");
        GiftCardDto ledger = adminCard(card.getCode());
        assertThat(ledger.getTransactions()).extracting(GiftCardDto -> GiftCardDto.getType())
                .containsExactly(GiftCardTransactionType.ISSUED, GiftCardTransactionType.REDEEMED);
        assertThat(ledger.getTransactions().getLast().getAmount()).isEqualByComparingTo("-30.00");
        assertThat(ledger.getTransactions().getLast().getOrderId()).isEqualTo(created.getId());
        assertThat(ledger.getTransactions().getLast().getBalanceAfter()).isEqualByComparingTo("0.00");
        assertThat(cart(clientToken).getGiftCardCode()).isNull();
    }

    @Test
    void checkoutWithPointsDebitsTheAccount() {
        grantPoints(client.getUsername(), 1_000);
        addToCart(clientToken, product("12.34", 5), 1);
        applyPoints(clientToken, 500, CartDto.class);

        OrderDto order = checkout(clientToken);

        assertThat(order.getPointsRedeemed()).isEqualTo(500L);
        assertThat(order.getPointsAmount()).isEqualByComparingTo("5.00");
        assertThat(order.getAmountDue()).isEqualByComparingTo("7.34");
        assertThat(order.getGiftCardCode()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(loyalty(clientToken).getBalance()).isEqualTo(500);
        assertThat(ledger(clientToken)).first().satisfies(entry -> {
            assertThat(entry.getType()).isEqualTo(LoyaltyTransactionType.REDEEMED);
            assertThat(entry.getPoints()).isEqualTo(-500);
            assertThat(entry.getBalanceAfter()).isEqualTo(500);
            assertThat(entry.getOrderId()).isEqualTo(order.getId());
        });
    }

    @Test
    void checkoutWithBothUsesTheGiftCardFirstAndCapsPoints() {
        GiftCardDto card = issueGiftCard("10.00");
        grantPoints(client.getUsername(), 5_000);
        addToCart(clientToken, product("14.50", 5), 2);
        applyGiftCard(clientToken, card.getCode(), CartDto.class);
        applyPoints(clientToken, 1_000, CartDto.class);

        OrderDto order = checkout(clientToken);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("29.00");
        assertThat(order.getGiftCardAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getPointsRedeemed()).isEqualTo(1_000L);
        assertThat(order.getPointsAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getAmountDue()).isEqualByComparingTo("9.00");
        assertThat(loyalty(clientToken).getBalance()).isEqualTo(4_000);
        assertThat(executeGet(ORDERS_ENDPOINT, getHeadersWith(clientToken), new ParameterizedTypeReference<PageDto<OrderDto>>() {})
                .getBody().getContent()).singleElement().isEqualTo(order);
    }

    @Test
    void tendersCoveringTheWholeTotalLeaveCheckoutPendingAndThenMarkTheOrderPaid() {
        GiftCardDto card = issueGiftCard("50.00");
        grantPoints(client.getUsername(), 5_000);
        addToCart(clientToken, product("30.00", 5), 2);
        applyGiftCard(clientToken, card.getCode(), CartDto.class);
        applyPoints(clientToken, 5_000, CartDto.class);

        OrderDto created = checkout(clientToken);
        OrderDto fetched = executeGet(ORDERS_ENDPOINT + "/" + created.getId(), getHeadersWith(clientToken), OrderDto.class).getBody();

        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(created.getAmountDue()).isEqualByComparingTo("0.00");
        assertThat(created.getPointsRedeemed()).isEqualTo(1_000L);
        assertThat(fetched.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(fetched.getAmountDue()).isEqualByComparingTo("0.00");
        assertThat(fetched.getGiftCardAmount()).isEqualByComparingTo(created.getGiftCardAmount());
        assertThat(recorded.typesForOrder(created.getId())).containsExactlyInAnyOrder(ORDER_CREATED, ORDER_PAID);
        assertThat(loyalty(clientToken).getBalance()).isEqualTo(5_000 - 1_000 + 60);
    }

    @Test
    void couponCoveringEverythingLeavesTheOrderPendingAndTendersUntouched() {
        executePost("/api/v1/admin/coupons", CouponRequestDto.builder().code("FREE100").type(CouponType.FIXED_AMOUNT)
                .value(new BigDecimal("100")).build(), getHeadersWith(adminToken), String.class);
        GiftCardDto card = issueGiftCard("10.00");
        addToCart(clientToken, product("20.00", 5), 1);
        executePut("/api/v1/cart/coupon", new ApplyCouponDto("FREE100"), getHeadersWith(clientToken), String.class);
        CartDto preview = applyGiftCard(clientToken, card.getCode(), CartDto.class).getBody();

        OrderDto order = checkout(clientToken);

        assertThat(preview.getGiftCardAmount()).isEqualByComparingTo("0.00");
        assertThat(preview.getAmountDue()).isEqualByComparingTo("0.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getGiftCardCode()).isNull();
        assertThat(order.getAmountDue()).isNull();
        assertThat(balanceOf(card.getCode())).isEqualByComparingTo("10.00");
    }

    @Test
    void checkoutRejectsACardDisabledOrExpiredAfterItWasAppliedAndKeepsTheCart() {
        GiftCardDto disabled = issueGiftCard("10.00");
        addToCart(clientToken, product("20.00", 5), 1);
        applyGiftCard(clientToken, disabled.getCode(), CartDto.class);
        executePost(ADMIN_GIFT_CARDS + "/" + disabled.getCode() + "/disable", null, getHeadersWith(adminToken), GiftCardDto.class);

        ResponseEntity<ErrorDto> rejectedDisabled = executePost(ORDERS_ENDPOINT, getRandomAddress(), getHeadersWith(clientToken), ErrorDto.class);
        GiftCardDto expired = issueGiftCard("10.00");
        applyGiftCard(clientToken, expired.getCode(), CartDto.class);
        expire(expired.getCode());
        ResponseEntity<ErrorDto> rejectedExpired = executePost(ORDERS_ENDPOINT, getRandomAddress(), getHeadersWith(clientToken), ErrorDto.class);

        assertThat(rejectedDisabled.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejectedDisabled.getBody().getMessage()).isEqualTo("Applied gift card is no longer valid: Gift card is disabled");
        assertThat(rejectedExpired.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejectedExpired.getBody().getMessage()).isEqualTo("Applied gift card is no longer valid: Gift card has expired");
        assertThat(cart(clientToken).getItems()).hasSize(1);
        assertThat(cart(clientToken).getGiftCardCode()).isEqualTo(expired.getCode());
        assertThat(balanceOf(expired.getCode())).isEqualByComparingTo("10.00");
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void checkoutRollsBackTheGiftCardWhenPointsAreNoLongerAvailable() {
        GiftCardDto card = issueGiftCard("5.00");
        grantPoints(client.getUsername(), 1_000);
        addToCart(clientToken, product("40.00", 5), 1);
        applyGiftCard(clientToken, card.getCode(), CartDto.class);
        applyPoints(clientToken, 1_000, CartDto.class);
        grantPoints(client.getUsername(), 300);

        ResponseEntity<ErrorDto> rejected = executePost(ORDERS_ENDPOINT, getRandomAddress(), getHeadersWith(clientToken), ErrorDto.class);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getBody().getMessage())
                .isEqualTo("Applied loyalty points are no longer available: Insufficient loyalty points");
        assertThat(balanceOf(card.getCode())).isEqualByComparingTo("5.00");
        assertThat(loyalty(clientToken).getBalance()).isEqualTo(300);
        assertThat(cart(clientToken).getPointsRedeemed()).isEqualTo(1_000L);
    }

    @Test
    void aSharedCardIsNeverSpentTwice() {
        GiftCardDto card = issueGiftCard("50.00");
        String thirdToken = getToken(com.awesome.testing.factory.UserFactory.getRandomUserWithRoles(
                List.of(com.awesome.testing.dto.user.Role.ROLE_CLIENT)));
        ProductEntity product = product("40.00", 10);
        for (String token : List.of(clientToken, otherClientToken, thirdToken)) {
            addToCart(token, product, 1);
            applyGiftCard(token, card.getCode(), CartDto.class);
        }

        OrderDto first = checkout(clientToken);
        OrderDto second = checkout(otherClientToken);
        ResponseEntity<ErrorDto> third = executePost(ORDERS_ENDPOINT, getRandomAddress(), getHeadersWith(thirdToken), ErrorDto.class);

        assertThat(first.getGiftCardAmount()).isEqualByComparingTo("40.00");
        assertThat(second.getGiftCardAmount()).isEqualByComparingTo("10.00");
        assertThat(second.getAmountDue()).isEqualByComparingTo("30.00");
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(third.getBody().getMessage()).isEqualTo("Applied gift card is no longer valid: Gift card has no remaining balance");
        assertThat(balanceOf(card.getCode())).isEqualByComparingTo("0.00");
    }

    @Test
    void concurrentCheckoutsWithOneCardNeverOverspendIt() throws Exception {
        GiftCardDto card = issueGiftCard("50.00");
        ProductEntity product = product("40.00", 10);
        List<String> tokens = List.of(clientToken, otherClientToken);
        for (String token : tokens) {
            addToCart(token, product, 1);
            applyGiftCard(token, card.getCode(), CartDto.class);
        }

        List<ResponseEntity<OrderDto>> responses = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<ResponseEntity<OrderDto>>> calls = tokens.stream()
                    .<Callable<ResponseEntity<OrderDto>>>map(token -> () -> executePost(ORDERS_ENDPOINT,
                            getRandomAddress(), getHeadersWith(token), OrderDto.class))
                    .toList();
            for (Future<ResponseEntity<OrderDto>> future : executor.invokeAll(calls)) {
                responses.add(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        BigDecimal spent = responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.CREATED)
                .map(response -> response.getBody().getGiftCardAmount())
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long redemptions = adminCard(card.getCode()).getTransactions().stream()
                .filter(entry -> entry.getType() == GiftCardTransactionType.REDEEMED).count();
        assertThat(spent).isLessThanOrEqualTo(new BigDecimal("50.00"));
        assertThat(balanceOf(card.getCode()).add(spent)).isEqualByComparingTo("50.00");
        assertThat(redemptions).isEqualTo(responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.CREATED && response.getBody().getGiftCardAmount() != null)
                .count());
    }

    @Test
    void cancellationRestoresTheGiftCardAndPointsOnce() {
        GiftCardDto card = issueGiftCard("15.00");
        grantPoints(client.getUsername(), 1_000);
        addToCart(clientToken, product("40.00", 5), 1);
        applyGiftCard(clientToken, card.getCode(), CartDto.class);
        applyPoints(clientToken, 700, CartDto.class);
        OrderDto order = checkout(clientToken);

        ResponseEntity<OrderDto> cancelled = cancel(order.getId(), clientToken, OrderDto.class);
        ResponseEntity<ErrorDto> again = cancel(order.getId(), clientToken, ErrorDto.class);

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getBody().getGiftCardAmount()).isEqualByComparingTo("15.00");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(balanceOf(card.getCode())).isEqualByComparingTo("15.00");
        assertThat(loyalty(clientToken).getBalance()).isEqualTo(1_000);
        assertThat(adminCard(card.getCode()).getTransactions()).extracting(entry -> entry.getType())
                .containsExactly(GiftCardTransactionType.ISSUED, GiftCardTransactionType.REDEEMED, GiftCardTransactionType.RESTORED);
        assertThat(ledger(clientToken)).extracting(LoyaltyTransactionDto::getType)
                .containsExactly(LoyaltyTransactionType.RESTORED, LoyaltyTransactionType.REDEEMED);
    }

    @Test
    void cancellingAPaidOrderRevokesTheEarnedPointsAndRestoresTheTenders() {
        GiftCardDto card = issueGiftCard("10.00");
        addToCart(clientToken, product("75.50", 5), 1);
        applyGiftCard(clientToken, card.getCode(), CartDto.class);
        OrderDto order = checkout(clientToken);
        markPaid(order.getId());
        long earned = loyalty(clientToken).getBalance();

        ResponseEntity<OrderDto> cancelled = executePut(ORDERS_ENDPOINT + "/" + order.getId() + "/status",
                OrderStatus.CANCELLED, getHeadersWith(adminToken), OrderDto.class);

        assertThat(earned).isEqualTo(75);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loyalty(clientToken).getBalance()).isZero();
        assertThat(loyalty(clientToken).getLifetimeEarned()).isEqualTo(75);
        assertThat(balanceOf(card.getCode())).isEqualByComparingTo("10.00");
        assertThat(ledger(clientToken)).first().satisfies(entry -> {
            assertThat(entry.getType()).isEqualTo(LoyaltyTransactionType.REVOKED);
            assertThat(entry.getPoints()).isEqualTo(-75);
        });
    }

    @Test
    void adminPaidOrdersRefundEverythingToTheGiftCardThenPointsAndRevokeEarnedPoints() {
        GiftCardDto card = issueGiftCard("30.00");
        grantPoints(client.getUsername(), 2_000);
        addToCart(clientToken, product("100.00", 5), 1);
        applyGiftCard(clientToken, card.getCode(), CartDto.class);
        applyPoints(clientToken, 2_000, CartDto.class);
        OrderDto order = checkout(clientToken);
        deliver(order.getId());
        assertThat(loyalty(clientToken).getBalance()).isEqualTo(100);
        OrderRefundDto first = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("25.00"), "Return #1");
        OrderRefundDto second = new OrderRefundDto(UUID.randomUUID(), new BigDecimal("75.00"), "Return #2");

        refund(order.getId(), first, String.class);
        BigDecimal afterFirst = balanceOf(card.getCode());
        long pointsAfterFirst = loyalty(clientToken).getBalance();
        refund(order.getId(), first, String.class);
        refund(order.getId(), second, String.class);

        assertThat(order.getAmountDue()).isEqualByComparingTo("50.00");
        assertThat(afterFirst).isEqualByComparingTo("25.00");
        assertThat(pointsAfterFirst).isEqualTo(75);
        assertThat(balanceOf(card.getCode())).isEqualByComparingTo("30.00");
        assertThat(loyalty(clientToken).getBalance()).isEqualTo(2_000);
        assertThat(ledger(clientToken)).extracting(LoyaltyTransactionDto::getType, LoyaltyTransactionDto::getPoints)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(LoyaltyTransactionType.REVOKED, -75L),
                        org.assertj.core.groups.Tuple.tuple(LoyaltyTransactionType.RESTORED, 2_000L),
                        org.assertj.core.groups.Tuple.tuple(LoyaltyTransactionType.REVOKED, -25L),
                        org.assertj.core.groups.Tuple.tuple(LoyaltyTransactionType.EARNED, 100L),
                        org.assertj.core.groups.Tuple.tuple(LoyaltyTransactionType.REDEEMED, -2_000L));
    }

    @Test
    void payingAnOrderEarnsPointsOnceAndEmailsTheCustomer() {
        ProductEntity product = product("123.99", 5);
        OrderDto order = order(clientToken, product, 1);

        markPaid(order.getId());
        executePut(ORDERS_ENDPOINT + "/" + order.getId() + "/status", OrderStatus.PENDING, getHeadersWith(adminToken), OrderDto.class);
        markPaid(order.getId());

        assertThat(loyalty(clientToken).getBalance()).isEqualTo(123);
        assertThat(loyalty(clientToken).getLifetimeEarned()).isEqualTo(123);
        assertThat(loyalty(clientToken).getValueOfBalance()).isEqualByComparingTo("1.23");
        assertThat(ledger(clientToken)).singleElement().satisfies(entry -> {
            assertThat(entry.getType()).isEqualTo(LoyaltyTransactionType.EARNED);
            assertThat(entry.getPoints()).isEqualTo(123);
            assertThat(entry.getOrderId()).isEqualTo(order.getId());
        });
        assertThat(emailEventRepository.findAll().stream()
                .filter(event -> event.getType() == EmailTemplate.LOYALTY_POINTS_EARNED)
                .filter(event -> event.getRecipientEmail().equals(client.getEmail())))
                .singleElement()
                .satisfies(event -> assertThat(event.getPayload()).contains("123"));
        assertThat(loyalty(otherClientToken).getBalance()).isZero();
    }

    @Test
    void cancellingBeforePaymentEarnsNothing() {
        OrderDto order = order(clientToken, product("50.00", 5), 1);

        cancel(order.getId(), clientToken, OrderDto.class);

        assertThat(loyalty(clientToken).getBalance()).isZero();
        assertThat(ledger(clientToken)).isEmpty();
    }

    private GiftCardDto adminCard(String code) {
        return executeGet(ADMIN_GIFT_CARDS + "/" + code, getHeadersWith(adminToken), GiftCardDto.class).getBody();
    }

    private List<LoyaltyTransactionDto> ledger(String token) {
        ResponseEntity<PageDto<LoyaltyTransactionDto>> response = executeGet(LOYALTY + "/transactions?size=100",
                getHeadersWith(token), LEDGER);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getContent();
    }
}
