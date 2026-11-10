package com.awesome.testing.service.loyalty;

import com.awesome.testing.dto.loyalty.LoyaltyTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoyaltyPointsTest {

    @ParameterizedTest
    @CsvSource({"0.00, 0", "0.99, 0", "1.00, 1", "1.99, 1", "123.99, 123", "-5.00, 0"})
    void earnsOnePointPerFullUnit(String total, long expected) {
        assertThat(LoyaltyPoints.earnedFor(new BigDecimal(total))).isEqualTo(expected);
    }

    @Test
    void earnsNothingWithoutATotal() {
        assertThat(LoyaltyPoints.earnedFor(null)).isZero();
    }

    @Test
    void hundredPointsAreWorthOneUnit() {
        assertThat(LoyaltyPoints.valueOf(0)).isEqualByComparingTo("0").hasToString("0.00");
        assertThat(LoyaltyPoints.valueOf(150)).hasToString("1.50");
        assertThat(LoyaltyPoints.valueOf(12_345)).hasToString("123.45");
        assertThat(LoyaltyPoints.pointsFor(new BigDecimal("7.34"))).isEqualTo(734);
        assertThat(LoyaltyPoints.pointsFor(new BigDecimal("2"))).isEqualTo(200);
        assertThatThrownBy(() -> LoyaltyPoints.pointsFor(new BigDecimal("0.005"))).isInstanceOf(ArithmeticException.class);
    }

    @ParameterizedTest
    @CsvSource({"100, true", "500, true", "0, false", "50, false", "150, false", "-100, false"})
    void redeemsWholeMultiplesOfOneHundred(long points, boolean redeemable) {
        assertThat(LoyaltyPoints.isRedeemable(points)).isEqualTo(redeemable);
    }

    @ParameterizedTest
    @CsvSource({
            "100, 100.00, 60.00, 60",
            "100, 100.00, 100.00, 100",
            "100, 100.00, 150.00, 100",
            "75, 75.50, 10.00, 9",
            "7, 7.99, 3.99, 3",
            "0, 10.00, 10.00, 0",
            "50, 0.00, 10.00, 0",
            "50, 10.00, 0.00, 0"})
    void revokesTheRefundedShareRoundedDown(long earned, String total, String refunded, long expected) {
        assertThat(LoyaltyPoints.revocableFor(earned, new BigDecimal(total), new BigDecimal(refunded))).isEqualTo(expected);
    }

    @Test
    void revokesNothingWithoutAmounts() {
        assertThat(LoyaltyPoints.revocableFor(10, null, BigDecimal.ONE)).isZero();
        assertThat(LoyaltyPoints.revocableFor(10, BigDecimal.TEN, null)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"0, BRONZE", "999, BRONZE", "1000, SILVER", "4999, SILVER", "5000, GOLD"})
    void tiersFollowLifetimePoints(long lifetime, LoyaltyTier tier) {
        assertThat(LoyaltyPoints.tierFor(lifetime)).isEqualTo(tier);
    }
}
