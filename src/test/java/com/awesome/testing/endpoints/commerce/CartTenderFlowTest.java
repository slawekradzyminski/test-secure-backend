package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.coupon.ApplyCouponDto;
import com.awesome.testing.dto.coupon.CouponRequestDto;
import com.awesome.testing.dto.giftcard.ApplyGiftCardDto;
import com.awesome.testing.dto.giftcard.GiftCardDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.coupon.CouponType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Applying gift cards and loyalty points to the cart, like coupons. */
class CartTenderFlowTest extends AbstractTenderFlowTest {

    private static final Set<String> LEGACY_CART = Set.of("username", "items", "totalPrice", "totalItems");

    @Test
    void cartJsonIsByteIdenticalWhenNothingIsApplied() {
        addToCart(clientToken, product("19.99", 5), 2);
        grantPoints(client.getUsername(), 1000);
        GiftCardDto card = issueGiftCard("10.00");

        String before = executeGet(CART_ENDPOINT, getHeadersWith(clientToken), String.class).getBody();
        applyGiftCard(clientToken, card.getCode(), CartDto.class);
        applyPoints(clientToken, 200, CartDto.class);
        ResponseEntity<String> withoutGiftCard = executeDelete(CART_GIFT_CARD, getHeadersWith(clientToken), String.class);
        ResponseEntity<String> withoutPoints = executeDelete(CART_POINTS, getHeadersWith(clientToken), String.class);
        String after = executeGet(CART_ENDPOINT, getHeadersWith(clientToken), String.class).getBody();

        assertThat(fields(before)).isEqualTo(LEGACY_CART);
        assertThat(after).isEqualTo(before);
        assertThat(fields(withoutGiftCard.getBody())).contains("pointsRedeemed", "amountDue").doesNotContain("giftCardCode");
        assertThat(withoutPoints.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withoutPoints.getBody()).isEqualTo(before);
    }

    @Test
    void removingWhatIsNotAppliedSucceedsAndKeepsTheLegacyCart() {
        addToCart(clientToken, product("5.00", 5), 1);

        ResponseEntity<String> giftCard = executeDelete(CART_GIFT_CARD, getHeadersWith(clientToken), String.class);
        ResponseEntity<String> points = executeDelete(CART_POINTS, getHeadersWith(clientToken), String.class);

        assertThat(giftCard.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fields(giftCard.getBody())).isEqualTo(LEGACY_CART);
        assertThat(points.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fields(points.getBody())).isEqualTo(LEGACY_CART);
    }

    @Test
    void giftCardPaysAfterTheCouponAndPointsPayTheRestCappedAtZero() {
        executePost("/api/v1/admin/coupons", CouponRequestDto.builder().code("TENDER10").type(CouponType.PERCENTAGE)
                .value(BigDecimal.TEN).build(), getHeadersWith(adminToken), String.class);
        addToCart(clientToken, product("50.00", 5), 2);
        executePut("/api/v1/cart/coupon", new ApplyCouponDto("TENDER10"), getHeadersWith(clientToken), String.class);
        GiftCardDto card = issueGiftCard("60.00");
        grantPoints(client.getUsername(), 10_000);

        CartDto withCard = applyGiftCard(clientToken, card.getCode().toLowerCase(), CartDto.class).getBody();
        CartDto withPoints = applyPoints(clientToken, 2_000, CartDto.class).getBody();
        CartDto capped = applyPoints(clientToken, 10_000, CartDto.class).getBody();

        assertThat(withCard.getTotalAfterDiscount()).isEqualByComparingTo("90.00");
        assertThat(withCard.getGiftCardCode()).isEqualTo(card.getCode());
        assertThat(withCard.getGiftCardAmount()).isEqualByComparingTo("60.00");
        assertThat(withCard.getPointsRedeemed()).isNull();
        assertThat(withCard.getAmountDue()).isEqualByComparingTo("30.00");
        assertThat(withPoints.getPointsRedeemed()).isEqualTo(2_000L);
        assertThat(withPoints.getPointsAmount()).isEqualByComparingTo("20.00");
        assertThat(withPoints.getAmountDue()).isEqualByComparingTo("10.00");
        assertThat(capped.getPointsRedeemed()).isEqualTo(3_000L);
        assertThat(capped.getPointsAmount()).isEqualByComparingTo("30.00");
        assertThat(capped.getAmountDue()).isEqualByComparingTo("0.00");
    }

    @Test
    void cardThatCanNoLongerPayPreviewsAsZero() {
        addToCart(clientToken, product("20.00", 5), 1);
        GiftCardDto card = issueGiftCard("15.00");
        applyGiftCard(clientToken, card.getCode(), CartDto.class);

        executePost(ADMIN_GIFT_CARDS + "/" + card.getCode() + "/disable", null, getHeadersWith(adminToken), GiftCardDto.class);
        CartDto cart = cart(clientToken);

        assertThat(cart.getGiftCardCode()).isEqualTo(card.getCode());
        assertThat(cart.getGiftCardAmount()).isEqualByComparingTo("0.00");
        assertThat(cart.getAmountDue()).isEqualByComparingTo("20.00");
    }

    @Test
    void applyingAGiftCardReportsEachFailure() {
        GiftCardDto disabled = issueGiftCard("10.00");
        GiftCardDto expired = issueGiftCard("10.00");
        GiftCardDto empty = issueGiftCard("10.00");
        executePost(ADMIN_GIFT_CARDS + "/" + disabled.getCode() + "/disable", null, getHeadersWith(adminToken), GiftCardDto.class);
        expire(expired.getCode());
        var drained = giftCardRepository.findByCode(empty.getCode()).orElseThrow();
        drained.setBalance(BigDecimal.ZERO);
        giftCardRepository.save(drained);

        ResponseEntity<ErrorDto> emptyCart = applyGiftCard(clientToken, disabled.getCode(), ErrorDto.class);
        addToCart(clientToken, product("20.00", 5), 1);
        ResponseEntity<ErrorDto> disabledCard = applyGiftCard(clientToken, disabled.getCode(), ErrorDto.class);
        ResponseEntity<ErrorDto> expiredCard = applyGiftCard(clientToken, expired.getCode(), ErrorDto.class);
        ResponseEntity<ErrorDto> emptyCard = applyGiftCard(clientToken, empty.getCode(), ErrorDto.class);
        ResponseEntity<ErrorDto> unknown = applyGiftCard(clientToken, "GC-AAAA-AAAA-AAAA", ErrorDto.class);
        ResponseEntity<ErrorDto> malformed = applyGiftCard(clientToken, "WELCOME10", ErrorDto.class);
        ResponseEntity<Map> blank = executePut(CART_GIFT_CARD, new ApplyGiftCardDto(" "), getHeadersWith(clientToken), Map.class);
        ResponseEntity<String> anonymous = executePut(CART_GIFT_CARD, new ApplyGiftCardDto(disabled.getCode()),
                getJsonOnlyHeaders(), String.class);

        assertThat(emptyCart.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(emptyCart.getBody().getMessage()).isEqualTo("Cart is empty");
        assertThat(disabledCard.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(disabledCard.getBody().getMessage()).isEqualTo("Gift card is disabled");
        assertThat(expiredCard.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(expiredCard.getBody().getMessage()).isEqualTo("Gift card has expired");
        assertThat(emptyCard.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(emptyCard.getBody().getMessage()).isEqualTo("Gift card has no remaining balance");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getBody()).containsKey("code");
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(fields(executeGet(CART_ENDPOINT, getHeadersWith(clientToken), String.class).getBody())).isEqualTo(LEGACY_CART);
    }

    @Test
    void applyingPointsValidatesStepAndBalance() {
        grantPoints(client.getUsername(), 500);

        ResponseEntity<ErrorDto> emptyCart = applyPoints(clientToken, 100, ErrorDto.class);
        addToCart(clientToken, product("20.00", 5), 1);
        ResponseEntity<ErrorDto> notAStep = applyPoints(clientToken, 150, ErrorDto.class);
        ResponseEntity<Map> belowMinimum = applyPoints(clientToken, 50, Map.class);
        ResponseEntity<ErrorDto> tooMany = applyPoints(clientToken, 600, ErrorDto.class);
        addToCart(otherClientToken, product("20.00", 5), 1);
        ResponseEntity<ErrorDto> noAccount = applyPoints(otherClientToken, 100, ErrorDto.class);
        ResponseEntity<CartDto> exact = applyPoints(clientToken, 500, CartDto.class);
        ResponseEntity<String> anonymous = executePut(CART_POINTS, Map.of("points", 100), getJsonOnlyHeaders(), String.class);

        assertThat(emptyCart.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(emptyCart.getBody().getMessage()).isEqualTo("Cart is empty");
        assertThat(notAStep.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notAStep.getBody().getMessage()).isEqualTo("Points must be a whole multiple of 100");
        assertThat(belowMinimum.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(belowMinimum.getBody()).containsKey("points");
        assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tooMany.getBody().getMessage()).isEqualTo("Insufficient loyalty points");
        assertThat(noAccount.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exact.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exact.getBody().getPointsRedeemed()).isEqualTo(500L);
        assertThat(exact.getBody().getPointsAmount()).isEqualByComparingTo("5.00");
        assertThat(exact.getBody().getAmountDue()).isEqualByComparingTo("15.00");
        assertThat(exact.getBody().getGiftCardCode()).isNull();
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void clearingTheCartDropsTheGiftCardAndPoints() {
        ProductEntity product = product("20.00", 5);
        addToCart(clientToken, product, 1);
        grantPoints(client.getUsername(), 500);
        applyGiftCard(clientToken, issueGiftCard("5.00").getCode(), CartDto.class);
        applyPoints(clientToken, 100, CartDto.class);

        executeDelete(CART_ENDPOINT, getHeadersWith(clientToken), String.class);
        addToCart(clientToken, product, 1);

        assertThat(fields(executeGet(CART_ENDPOINT, getHeadersWith(clientToken), String.class).getBody())).isEqualTo(LEGACY_CART);
    }

    @Test
    void removeEndpointsNeedAToken() {
        assertThat(executeDelete(CART_GIFT_CARD, getJsonOnlyHeaders(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(executeDelete(CART_POINTS, getJsonOnlyHeaders(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static Set<String> fields(String json) {
        JsonNode node = JsonMapper.builder().build().readTree(json);
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
