package com.awesome.testing.service.coupon;

import com.awesome.testing.entity.coupon.CouponType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CouponPricingTest {

    @ParameterizedTest(name = "{0} {1} of {2} = {3}")
    @CsvSource({
            "PERCENTAGE, 10, 99.99, 10.00",
            "PERCENTAGE, 15, 0.05, 0.01",
            "PERCENTAGE, 100, 42.10, 42.10",
            "PERCENTAGE, 12.5, 19.99, 2.50",
            "FIXED_AMOUNT, 20, 99.99, 20.00",
            "FIXED_AMOUNT, 150, 99.99, 99.99",
            "FIXED_AMOUNT, 5, 0, 0.00"
    })
    void discountIsRoundedHalfUpAndCappedAtTheSubtotal(CouponType type, String value, String subtotal, String expected) {
        assertThat(CouponPricing.discount(type, new BigDecimal(value), new BigDecimal(subtotal)))
                .isEqualByComparingTo(expected)
                .hasScaleOf(2);
    }

    @ParameterizedTest(name = "refund of {0} for total {1}/{2} = {3}")
    @CsvSource({
            "120.00, 126.00, 140.00, 108.00",
            "33.33, 90.00, 100.00, 30.00",
            "50.00, 50.00, 50.00, 50.00",
            "10.00, 0.00, 10.00, 0.00"
    })
    void refundScalesWithTheShareOfTheOrderThatWasPaid(String items, String total, String subtotal, String expected) {
        assertThat(CouponPricing.proportionalRefund(new BigDecimal(items), new BigDecimal(total), new BigDecimal(subtotal)))
                .isEqualByComparingTo(expected);
    }

    @org.junit.jupiter.api.Test
    void refundForAnOrderWithoutAKnownSubtotalIsTheItemsValue() {
        assertThat(CouponPricing.proportionalRefund(new BigDecimal("12.345"), new BigDecimal("0.00"), BigDecimal.ZERO))
                .isEqualByComparingTo("12.35");
        assertThat(CouponPricing.proportionalRefund(new BigDecimal("12.30"), new BigDecimal("10.00"), null))
                .isEqualByComparingTo("12.30");
    }
}
