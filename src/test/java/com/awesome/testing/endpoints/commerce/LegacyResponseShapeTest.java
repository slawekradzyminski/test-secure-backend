package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.coupon.ApplyCouponDto;
import com.awesome.testing.dto.coupon.CouponRequestDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.coupon.CouponType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AI Testers course compares whole cart and order bodies with toEqual, so
 * responses without a coupon must keep exactly the fields they had before
 * coupons existed. Coupon fields appear only when a coupon is involved.
 */
class LegacyResponseShapeTest extends AbstractCommerceFlowTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> LEGACY_CART = Set.of("username", "items", "totalPrice", "totalItems");
    private static final Set<String> LEGACY_ORDER = Set.of(
            "id", "username", "items", "totalAmount", "status", "shippingAddress", "createdAt", "updatedAt",
            // 3.21.0: the reservation end, identical in POST, GET and list bodies (OrderReservationFlowTest).
            "reservationExpiresAt");

    @Test
    void cartAndOrderWithoutCouponKeepTheirOriginalFields() {
        ProductEntity product = product("12.50", 5);
        addToCart(clientToken, product, 2);

        JsonNode cart = body(executeGet(CART_ENDPOINT, getHeadersWith(clientToken), String.class));
        JsonNode order = body(executePost(ORDERS_ENDPOINT, getRandomAddress(), getHeadersWith(clientToken), String.class));
        JsonNode emptyCart = body(executeGet(CART_ENDPOINT, getHeadersWith(clientToken), String.class));

        assertThat(fields(cart)).isEqualTo(LEGACY_CART);
        assertThat(fields(order)).isEqualTo(LEGACY_ORDER);
        assertThat(fields(emptyCart)).isEqualTo(LEGACY_CART);
    }

    @Test
    void couponFieldsAppearOnlyWhenACouponIsInvolved() {
        executePost("/api/v1/admin/coupons", CouponRequestDto.builder().code("SHAPE10").type(CouponType.PERCENTAGE)
                .value(BigDecimal.TEN).build(), getHeadersWith(adminToken), String.class);
        addToCart(clientToken, product("20.00", 5), 1);
        executePut("/api/v1/cart/coupon", new ApplyCouponDto("SHAPE10"), getHeadersWith(clientToken), String.class);

        JsonNode cart = body(executeGet(CART_ENDPOINT, getHeadersWith(clientToken), String.class));
        JsonNode order = body(executePost(ORDERS_ENDPOINT, getRandomAddress(), getHeadersWith(clientToken), String.class));

        assertThat(fields(cart)).contains("couponCode", "couponValid", "discountAmount", "totalAfterDiscount")
                .doesNotContain("couponMessage");
        assertThat(order.get("couponCode").asString()).isEqualTo("SHAPE10");
        assertThat(order.get("discountAmount").decimalValue()).isEqualByComparingTo("2.00");
        assertThat(order.get("subtotalAmount").decimalValue()).isEqualByComparingTo("20.00");
    }

    private static JsonNode body(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
        return JSON.readTree(response.getBody());
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
