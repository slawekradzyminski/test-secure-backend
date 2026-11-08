package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.coupon.ApplyCouponDto;
import com.awesome.testing.dto.coupon.CouponDto;
import com.awesome.testing.dto.coupon.CouponRequestDto;
import com.awesome.testing.dto.coupon.CouponStatus;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.coupon.CouponType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static org.assertj.core.api.Assertions.assertThat;

class CouponFlowTest extends AbstractCommerceFlowTest {

    private static final String COUPONS = "/api/v1/admin/coupons";
    private static final String CART_COUPON = "/api/v1/cart/coupon";

    @Test
    void percentageCouponIsPreviewedInCartAndRedeemedAtCheckout() {
        createCoupon(coupon("welcome10", CouponType.PERCENTAGE, "10").minOrderAmount(new BigDecimal("50.00")));
        ProductEntity product = product("33.33", 10);
        addToCart(clientToken, product, 3);

        ResponseEntity<CartDto> cart = applyCoupon(clientToken, " Welcome10 ", CartDto.class);
        OrderDto order = checkout(clientToken);

        assertThat(cart.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cart.getBody().getCouponCode()).isEqualTo("WELCOME10");
        assertThat(cart.getBody().getCouponValid()).isTrue();
        assertThat(cart.getBody().getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(cart.getBody().getTotalAfterDiscount()).isEqualByComparingTo("89.99");
        assertThat(order.getSubtotalAmount()).isEqualByComparingTo("99.99");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("89.99");
        assertThat(order.getCouponCode()).isEqualTo("WELCOME10");
    }

    @Test
    void fixedCouponNeverDiscountsMoreThanTheSubtotal() {
        createCoupon(coupon("BIG100", CouponType.FIXED_AMOUNT, "100"));
        addToCart(clientToken, product("40.00", 5), 1);

        applyCoupon(clientToken, "BIG100", CartDto.class);
        OrderDto order = checkout(clientToken);

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("40.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void cartShowsCouponAsInvalidWhenTheSubtotalDropsBelowTheMinimumAndCheckoutRejectsIt() {
        createCoupon(coupon("MIN100", CouponType.FIXED_AMOUNT, "10").minOrderAmount(new BigDecimal("100.00")));
        ProductEntity product = product("60.00", 10);
        addToCart(clientToken, product, 2);
        applyCoupon(clientToken, "MIN100", CartDto.class);
        executePut(CART_ITEMS_ENDPOINT + "/" + product.getId(),
                java.util.Map.of("quantity", 1), getHeadersWith(clientToken), CartDto.class);

        CartDto cart = executeGet(CART_ENDPOINT, getHeadersWith(clientToken), CartDto.class).getBody();
        ResponseEntity<ErrorDto> checkout = executePost(ORDERS_ENDPOINT, getRandomAddress(), getHeadersWith(clientToken), ErrorDto.class);

        assertThat(cart.getCouponValid()).isFalse();
        assertThat(cart.getCouponMessage()).isEqualTo("Order subtotal must be at least 100.00");
        assertThat(cart.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(checkout.getBody().getMessage())
                .isEqualTo("Applied coupon is no longer valid: Order subtotal must be at least 100.00");
        assertThat(executeGet(CART_ENDPOINT, getHeadersWith(clientToken), CartDto.class).getBody().getItems()).hasSize(1);
    }

    @Test
    void perUserLimitIsEnforcedAndCancellationReleasesTheRedemption() {
        CouponDto coupon = createCoupon(coupon("ONCE", CouponType.PERCENTAGE, "5"));
        ProductEntity product = product("20.00", 10);
        addToCart(clientToken, product, 1);
        applyCoupon(clientToken, "ONCE", CartDto.class);
        OrderDto first = checkout(clientToken);
        addToCart(clientToken, product, 1);

        ResponseEntity<ErrorDto> secondUse = applyCoupon(clientToken, "ONCE", ErrorDto.class);
        executePost(ORDERS_ENDPOINT + "/" + first.getId() + "/cancel", null, getHeadersWith(clientToken), OrderDto.class);
        ResponseEntity<CartDto> afterCancel = applyCoupon(clientToken, "ONCE", CartDto.class);
        CouponDto reloaded = executeGet(COUPONS + "/" + coupon.getId(), getHeadersWith(adminToken), CouponDto.class).getBody();

        assertThat(secondUse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondUse.getBody().getMessage()).isEqualTo("Coupon already used the maximum number of times");
        assertThat(afterCancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reloaded.getRedemptionCount()).isZero();
    }

    @Test
    void globalLimitBlocksOtherUsersOnceExhausted() {
        createCoupon(coupon("FIRST1", CouponType.PERCENTAGE, "5").maxRedemptions(1));
        ProductEntity product = product("20.00", 10);
        addToCart(clientToken, product, 1);
        applyCoupon(clientToken, "FIRST1", CartDto.class);
        addToCart(otherClientToken, product, 1);
        applyCoupon(otherClientToken, "FIRST1", CartDto.class);
        checkout(clientToken);

        ResponseEntity<ErrorDto> lateCheckout = executePost(ORDERS_ENDPOINT, getRandomAddress(),
                getHeadersWith(otherClientToken), ErrorDto.class);
        CouponDto listed = executeGet(COUPONS, getHeadersWith(adminToken),
                new org.springframework.core.ParameterizedTypeReference<com.awesome.testing.dto.order.PageDto<CouponDto>>() {})
                .getBody().getContent().getFirst();

        assertThat(lateCheckout.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lateCheckout.getBody().getMessage()).isEqualTo("Applied coupon is no longer valid: Coupon usage limit reached");
        assertThat(listed.getStatus()).isEqualTo(CouponStatus.EXHAUSTED);
    }

    @Test
    void inactiveExpiredAndFutureCouponsAreRejectedWithSpecificMessages() {
        LocalDateTime now = LocalDateTime.now();
        createCoupon(coupon("OFF", CouponType.PERCENTAGE, "5").active(false));
        createCoupon(coupon("OLD", CouponType.PERCENTAGE, "5").validFrom(now.minusDays(10)).validUntil(now.minusDays(1)));
        createCoupon(coupon("SOON", CouponType.PERCENTAGE, "5").validFrom(now.plusDays(1)));
        addToCart(clientToken, product("20.00", 10), 1);

        assertThat(applyCoupon(clientToken, "OFF", ErrorDto.class).getBody().getMessage()).isEqualTo("Coupon is not active");
        assertThat(applyCoupon(clientToken, "OLD", ErrorDto.class).getBody().getMessage()).isEqualTo("Coupon has expired");
        assertThat(applyCoupon(clientToken, "SOON", ErrorDto.class).getBody().getMessage()).isEqualTo("Coupon is not valid yet");
        assertThat(applyCoupon(clientToken, "NOPE", ErrorDto.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminValidationRejectsBadDefinitionsAndDuplicateCodes() {
        createCoupon(coupon("DUP", CouponType.PERCENTAGE, "5"));

        ResponseEntity<ErrorDto> duplicate = executePost(COUPONS, coupon("dup", CouponType.PERCENTAGE, "5").build(),
                getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<ErrorDto> tooMuch = executePost(COUPONS, coupon("HUGE", CouponType.PERCENTAGE, "100.01").build(),
                getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<String> badCode = executePost(COUPONS, coupon("a b", CouponType.PERCENTAGE, "5").build(),
                getHeadersWith(adminToken), String.class);
        ResponseEntity<String> clientAttempt = executePost(COUPONS, coupon("MINE", CouponType.PERCENTAGE, "5").build(),
                getHeadersWith(clientToken), String.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooMuch.getBody().getMessage()).isEqualTo("Percentage coupons cannot exceed 100");
        assertThat(badCode.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(clientAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void redeemedCouponCannotBeDeletedButUnusedCouponCan() {
        CouponDto used = createCoupon(coupon("USED", CouponType.PERCENTAGE, "5"));
        CouponDto unused = createCoupon(coupon("UNUSED", CouponType.PERCENTAGE, "5"));
        addToCart(clientToken, product("20.00", 10), 1);
        applyCoupon(clientToken, "USED", CartDto.class);
        checkout(clientToken);

        ResponseEntity<ErrorDto> deleteUsed = executeDelete(COUPONS + "/" + used.getId(), getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<Void> deleteUnused = executeDelete(COUPONS + "/" + unused.getId(), getHeadersWith(adminToken), Void.class);

        assertThat(deleteUsed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(deleteUnused.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private CouponRequestDto.CouponRequestDtoBuilder coupon(String code, CouponType type, String value) {
        return CouponRequestDto.builder().code(code).type(type).value(new BigDecimal(value));
    }

    private CouponDto createCoupon(CouponRequestDto.CouponRequestDtoBuilder coupon) {
        ResponseEntity<CouponDto> response = executePost(COUPONS, coupon.build(), getHeadersWith(adminToken), CouponDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private <T> ResponseEntity<T> applyCoupon(String token, String code, Class<T> type) {
        return executePut(CART_COUPON, new ApplyCouponDto(code), getHeadersWith(token), type);
    }
}
